package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RtmpTransportImpl : RtmpTransport {
    private var transport: RtmpStreamTransport? = null
    private val buffer = ByteArrayOutputStream()
    private var isFlvHeaderSkipped = false

    @Volatile
    private var rtmpState = "DISCONNECTED"

    // Diagnostics stats tracking
    private var connectStartTimeMs = 0L
    private var lastStatsResetTimeMs = System.currentTimeMillis()
    private var lastBytesSent = 0L
    private var lastPacketsSent = 0L
    private var currentFps = 0
    private var currentBitrateKbps = 0

    override fun connect(url: String, key: String): Boolean {
        buffer.reset()
        isFlvHeaderSkipped = false
        rtmpState = "CONNECTING"
        connectStartTimeMs = System.currentTimeMillis()

        // Build full URL
        val fullUrl = if (key.isNotEmpty()) {
            if (url.endsWith("/")) "$url$key" else "$url/$key"
        } else {
            url
        }

        val connectLatch = CountDownLatch(1)
        val connectSuccess = java.util.concurrent.atomic.AtomicBoolean(false)

        val t = RtmpStreamTransport(fullUrl)
        t.setListener(object : RtmpStreamTransport.Listener {
            override fun onTransportStateChanged(state: RtmpStreamTransport.State) {
                rtmpState = state.name
                if (state == RtmpStreamTransport.State.PUBLISHING) {
                    connectSuccess.set(true)
                    connectLatch.countDown()
                } else if (state == RtmpStreamTransport.State.ERROR || state == RtmpStreamTransport.State.STOPPED) {
                    connectSuccess.set(false)
                    connectLatch.countDown()
                }
            }

            override fun onTransportError(e: java.lang.Exception) {
                rtmpState = "ERROR"
                connectSuccess.set(false)
                connectLatch.countDown()
            }

            override fun onPacketDropped(packetType: Int, isKeyframe: Boolean) {
                // Drop metrics recorded inside transport
            }
        })

        transport = t
        t.start()

        // Synchronous non-polling wait using CountDownLatch completion primitive
        try {
            connectLatch.await(6000, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            connectSuccess.set(false)
        }

        return connectSuccess.get() && rtmpState == "PUBLISHING"
    }

    override fun sendMuxedData(data: ByteArray, length: Int, timestampUs: Long): Boolean {
        synchronized(buffer) {
            buffer.write(data, 0, length)
            parseFlvStream()
        }
        return true
    }

    override fun sendRtmpPacket(packet: RtmpPacket): Boolean {
        val t = transport ?: return false
        t.feedPacket(packet)
        return true
    }

    private fun parseFlvStream() {
        val bytes = buffer.toByteArray()
        var offset = 0
        val total = bytes.size

        // 1. Skip FLV signature and header if present
        if (!isFlvHeaderSkipped && total >= 13) {
            if (bytes[0] == 'F'.toByte() && bytes[1] == 'L'.toByte() && bytes[2] == 'V'.toByte()) {
                val headerLength = ((bytes[5].toInt() and 0xFF) shl 24) or
                        ((bytes[6].toInt() and 0xFF) shl 16) or
                        ((bytes[7].toInt() and 0xFF) shl 8) or
                        (bytes[8].toInt() and 0xFF)
                offset = headerLength + 4
                isFlvHeaderSkipped = true
            } else {
                isFlvHeaderSkipped = true
            }
        } else if (!isFlvHeaderSkipped) {
            return
        }

        while (total - offset >= 11) {
            val tagType = bytes[offset].toInt() and 0xFF
            val dataSize = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)

            val tsLow = ((bytes[offset + 4].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 6].toInt() and 0xFF)
            val tsHigh = bytes[offset + 7].toInt() and 0xFF
            val timestampMs = ((tsHigh.toLong() and 0xFFL) shl 24) or (tsLow.toLong() and 0xFFFFFFL)

            val neededBytes = 11 + dataSize + 4
            if (total - offset < neededBytes) {
                break
            }

            val payload = ByteArray(dataSize)
            System.arraycopy(bytes, offset + 11, payload, 0, dataSize)

            val isKeyframe = if (tagType == 9 && payload.isNotEmpty()) {
                (payload[0].toInt() and 0xF0) == 0x10
            } else {
                false
            }

            val packet = RtmpPacket(
                type = tagType,
                timestamp = timestampMs,
                payload = payload,
                isKeyframe = isKeyframe
            )

            transport?.feedPacket(packet)

            offset += neededBytes
        }

        if (offset > 0) {
            val remaining = total - offset
            val nextBuffer = ByteArrayOutputStream()
            nextBuffer.write(bytes, offset, remaining)
            buffer.reset()
            nextBuffer.writeTo(buffer)
        }
    }

    override fun disconnect() {
        transport?.stop()
        transport = null
        rtmpState = "DISCONNECTED"
    }

    override fun getStats(): LiveStreamStats {
        val t = transport
        val stats = t?.getStats()
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastStatsResetTimeMs

        val totalBytesSent = stats?.bytesSent ?: 0L
        val totalPacketsSent = stats?.sent ?: 0L
        val droppedFrames = stats?.dropped?.toInt() ?: 0

        if (elapsedMs >= 1000) {
            val bytesDelta = totalBytesSent - lastBytesSent
            val packetsDelta = totalPacketsSent - lastPacketsSent
            currentBitrateKbps = ((bytesDelta * 8) / elapsedMs).toInt()
            currentFps = ((packetsDelta * 1000) / elapsedMs).toInt()

            lastStatsResetTimeMs = now
            lastBytesSent = totalBytesSent
            lastPacketsSent = totalPacketsSent
        }

        return LiveStreamStats(
            fps = currentFps,
            bitrateKbps = currentBitrateKbps,
            totalBytesSent = totalBytesSent,
            droppedFrames = droppedFrames,
            rttMs = stats?.rttMs ?: 0L,
            rtmpState = rtmpState
        )
    }

    override fun release() {
        disconnect()
    }
}
