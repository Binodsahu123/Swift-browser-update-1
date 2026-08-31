package com.swift.browser.videoengine.live

import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue

class FlvMuxer(
    private val maxQueueSize: Int = 100,
    private val dropPolicy: DropPolicy = DropPolicy.DROP_NON_KEYFRAMES,
    var debugMode: Boolean = false
) {
    companion object {
        private const val TAG = "FlvMuxer"

        private fun logD(tag: String, msg: String) {
            try {
                Log.d(tag, msg)
            } catch (e: Throwable) {
                println("[$tag] $msg")
            }
        }

        private fun logW(tag: String, msg: String) {
            try {
                Log.w(tag, msg)
            } catch (e: Throwable) {
                println("[$tag] [WARN] $msg")
            }
        }
    }

    enum class DropPolicy {
        DROP_NON_KEYFRAMES,
        DROP_OLDEST,
        BLOCK
    }

    // Config options
    private var hasAudio = true
    private var hasVideo = true
    private var width = 0
    private var height = 0
    private var sampleRate = 44100
    private var channels = 2

    // Dynamic state
    private var isHeaderWritten = false
    private var lastTimestampMs = 0L
    private var sequenceHeaderVideoSent = false
    private var sequenceHeaderAudioSent = false

    // SPS/PPS cache
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    // Packet queue for consumer flow / backpressure
    private val packetQueue = LinkedBlockingQueue<RtmpPacket>()

    // Listeners
    private var packetListener: ((RtmpPacket) -> Unit)? = null
    private var rawFlvListener: ((ByteArray) -> Unit)? = null

    // Diagnostic Stats
    private var totalBytesMuxed = 0L
    private var videoFramesMuxed = 0L
    private var audioFramesMuxed = 0L
    private var droppedFrames = 0L

    fun setPacketListener(listener: (RtmpPacket) -> Unit) {
        this.packetListener = listener
    }

    fun setRawFlvListener(listener: (ByteArray) -> Unit) {
        this.rawFlvListener = listener
    }

    fun setAudioConfig(sampleRate: Int, channels: Int) {
        synchronized(this) {
            this.sampleRate = sampleRate
            this.channels = channels
        }
    }

    fun resetSequenceHeadersOnReconnect() {
        synchronized(this) {
            sequenceHeaderVideoSent = false
            sequenceHeaderAudioSent = false
            if (sps != null && pps != null && isHeaderWritten) {
                writeVideoSequenceHeader()
            }
            if (hasAudio && isHeaderWritten) {
                writeAudioSequenceHeader()
            }
        }
    }

    fun startMuxing(hasAudio: Boolean, hasVideo: Boolean, width: Int, height: Int) {
        synchronized(this) {
            this.hasAudio = hasAudio
            this.hasVideo = hasVideo
            this.width = width
            this.height = height

            isHeaderWritten = false
            sequenceHeaderVideoSent = false
            sequenceHeaderAudioSent = false
            lastTimestampMs = 0L
            packetQueue.clear()

            // 1. Generate FLV Header
            val headerBytes = FlvHeader.generate(hasAudio, hasVideo)
            val prevTag0 = byteArrayOf(0, 0, 0, 0)

            emitRawBytes(headerBytes)
            emitRawBytes(prevTag0)
            isHeaderWritten = true

            if (debugMode) {
                logD(TAG, "[MUX_DEBUG] FLV Header written. Audio: $hasAudio, Video: $hasVideo")
            }

            // 2. Generate and write onMetaData script tag
            writeMetadata()
        }
    }

    fun feedSpsPps(spsData: ByteArray, ppsData: ByteArray) {
        synchronized(this) {
            this.sps = spsData
            this.pps = ppsData
            if (isHeaderWritten && !sequenceHeaderVideoSent) {
                writeVideoSequenceHeader()
            }
        }
    }

    fun feedVideoFrame(frameData: ByteArray, presentationTimeUs: Long, isKeyframe: Boolean) {
        synchronized(this) {
            if (!isHeaderWritten) {
                logW(TAG, "FLV Header not written. Call startMuxing first.")
                return
            }

            // Parse Annex B start codes
            val nalus = AvcConfigRecord.splitAnnexB(frameData)
            if (nalus.isEmpty()) return

            // Auto-extract SPS/PPS if not yet supplied
            if (sps == null || pps == null) {
                var foundSps = sps
                var foundPps = pps
                for (nalu in nalus) {
                    if (nalu.isNotEmpty()) {
                        val naluType = nalu[0].toInt() and 0x1F
                        if (naluType == 7) {
                            foundSps = nalu
                        } else if (naluType == 8) {
                            foundPps = nalu
                        }
                    }
                }
                if (foundSps != null && foundPps != null) {
                    sps = foundSps
                    pps = foundPps
                    writeVideoSequenceHeader()
                }
            }

            val timestampMs = presentationTimeUs / 1000L
            val videoPacket = VideoTag.createVideoTag(nalus, timestampMs, isKeyframe)
            handleQueueAndPolicy(videoPacket)
        }
    }

    fun feedAudioFrame(frameData: ByteArray, presentationTimeUs: Long) {
        synchronized(this) {
            if (!isHeaderWritten) {
                logW(TAG, "FLV Header not written. Call startMuxing first.")
                return
            }

            if (!sequenceHeaderAudioSent) {
                writeAudioSequenceHeader()
            }

            val timestampMs = presentationTimeUs / 1000L
            val audioPacket = AudioTag.createAudioTag(frameData, timestampMs, channels)
            handleQueueAndPolicy(audioPacket)
        }
    }

    private fun writeVideoSequenceHeader() {
        val s = sps ?: return
        val p = pps ?: return
        if (sequenceHeaderVideoSent) return

        val avcRecord = AvcConfigRecord.generate(s, p)
        val seqHeaderPacket = VideoTag.createSequenceHeader(avcRecord, 0L)
        emitPacket(seqHeaderPacket)
        sequenceHeaderVideoSent = true

        if (debugMode) {
            logD(TAG, "[MUX_DEBUG] AVC sequence header emitted. SPS Size: ${s.size}, PPS Size: ${p.size}")
        }
    }

    private fun writeAudioSequenceHeader() {
        if (sequenceHeaderAudioSent) return
        
        val aacRecord = AacConfigRecord.generate(sampleRate, channels)
        val seqHeaderPacket = AudioTag.createSequenceHeader(aacRecord, 0L, channels)
        emitPacket(seqHeaderPacket)
        sequenceHeaderAudioSent = true

        if (debugMode) {
            logD(TAG, "[MUX_DEBUG] AAC AudioSpecificConfig emitted: ${hexDump(aacRecord)}")
        }
    }

    private fun writeMetadata() {
        val properties = mutableMapOf<String, Any>()
        properties["width"] = width.toDouble()
        properties["height"] = height.toDouble()
        properties["videocodecid"] = 7.0 // AVC
        
        if (hasAudio) {
            properties["audiocodecid"] = 10.0 // AAC
            properties["audiosamplerate"] = sampleRate.toDouble()
            properties["audiochannels"] = channels.toDouble()
        }

        val bos = ByteArrayOutputStream()
        val writer = AmfWriter(bos)
        writer.writeString("onMetaData")
        writer.writeEcmaArray(properties)

        val metadataPayload = bos.toByteArray()
        val packet = RtmpPacket(
            type = 18, // Script Data / Metadata
            timestamp = 0L,
            payload = metadataPayload
        )
        emitPacket(packet)
    }

    private fun handleQueueAndPolicy(packet: RtmpPacket) {
        synchronized(packetQueue) {
            if (packetQueue.size >= maxQueueSize) {
                when (dropPolicy) {
                    DropPolicy.DROP_NON_KEYFRAMES -> {
                        val iterator = packetQueue.iterator()
                        var dropped = false
                        while (iterator.hasNext()) {
                            val queued = iterator.next()
                            if (queued.type == 9 && !queued.isKeyframe) {
                                iterator.remove()
                                droppedFrames++
                                dropped = true
                                break
                            }
                        }
                        if (!dropped) {
                            val discarded = packetQueue.poll()
                            if (discarded != null) droppedFrames++
                        }
                    }
                    DropPolicy.DROP_OLDEST -> {
                        val discarded = packetQueue.poll()
                        if (discarded != null) droppedFrames++
                    }
                    DropPolicy.BLOCK -> {
                        var retries = 0
                        while (packetQueue.size >= maxQueueSize && retries < 10) {
                            try {
                                Thread.sleep(5)
                            } catch (e: InterruptedException) {
                                break
                            }
                            retries++
                        }
                        if (packetQueue.size >= maxQueueSize) {
                            val discarded = packetQueue.poll()
                            if (discarded != null) droppedFrames++
                        }
                    }
                }
            }
            packetQueue.add(packet)
        }
        emitPacket(packet)
    }

    private fun emitPacket(packet: RtmpPacket) {
        // Prevent timestamp regressions
        var adjustedTimestamp = packet.timestamp
        if (adjustedTimestamp < lastTimestampMs) {
            adjustedTimestamp = lastTimestampMs
        } else {
            lastTimestampMs = adjustedTimestamp
        }

        val finalPacket = if (adjustedTimestamp != packet.timestamp) {
            packet.copy(timestamp = adjustedTimestamp)
        } else {
            packet
        }

        packetListener?.invoke(finalPacket)

        val flvTagBytes = serializeToFlvTag(finalPacket)
        emitRawBytes(flvTagBytes)

        // Diagnostic counts
        totalBytesMuxed += flvTagBytes.size
        if (packet.type == 9) videoFramesMuxed++
        if (packet.type == 8) audioFramesMuxed++

        if (debugMode) {
            logD(TAG, "[MUX_DEBUG] Tag [Type: ${packet.type}, TS: ${adjustedTimestamp}ms, Payload size: ${packet.payload.size}]: ${hexDump(packet.payload, 16)}")
        }
    }

    private fun emitRawBytes(bytes: ByteArray) {
        rawFlvListener?.invoke(bytes)
    }

    fun pollMuxedPacket(): RtmpPacket? {
        return packetQueue.poll()
    }

    // --- FLV Tag Serialization ---
    fun serializeToFlvTag(packet: RtmpPacket): ByteArray {
        val size = packet.payload.size
        val timestamp = packet.timestamp
        val bos = ByteArrayOutputStream()

        // 1. Tag Type (1 byte)
        bos.write(packet.type)

        // 2. Data Size (3 bytes)
        bos.write((size shr 16) and 0xFF)
        bos.write((size shr 8) and 0xFF)
        bos.write(size and 0xFF)

        // 3. Timestamp Low (3 bytes)
        bos.write(((timestamp shr 16) and 0xFF).toInt())
        bos.write(((timestamp shr 8) and 0xFF).toInt())
        bos.write((timestamp and 0xFF).toInt())

        // 4. Timestamp Extended (1 byte)
        bos.write(((timestamp shr 24) and 0xFF).toInt())

        // 5. StreamID (3 bytes: always 0)
        bos.write(0)
        bos.write(0)
        bos.write(0)

        // 6. Tag Payload
        bos.write(packet.payload)

        // 7. Previous Tag Size (4 bytes)
        val prevSize = 11 + size
        bos.write((prevSize shr 24) and 0xFF)
        bos.write((prevSize shr 16) and 0xFF)
        bos.write((prevSize shr 8) and 0xFF)
        bos.write(prevSize and 0xFF)

        return bos.toByteArray()
    }

    private fun hexDump(bytes: ByteArray, limit: Int = 16): String {
        val sb = StringBuilder()
        val actualLimit = minOf(bytes.size, limit)
        for (i in 0 until actualLimit) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        if (bytes.size > limit) {
            sb.append("...")
        }
        return sb.toString()
    }

    // --- Diagnostics Metrics ---
    fun getTotalBytesMuxed(): Long = totalBytesMuxed
    fun getVideoFramesMuxed(): Long = videoFramesMuxed
    fun getAudioFramesMuxed(): Long = audioFramesMuxed
    fun getDroppedFrames(): Long = droppedFrames
    fun getQueueSize(): Int = packetQueue.size

    // --- AMF0 Writer for Metadata ---
    private class AmfWriter(private val bos: ByteArrayOutputStream) {
        
        fun writeString(str: String) {
            bos.write(2) // Short string marker
            val bytes = str.toByteArray(StandardCharsets.UTF_8)
            bos.write((bytes.size shr 8) and 0xFF)
            bos.write(bytes.size and 0xFF)
            bos.write(bytes)
        }

        fun writeNumber(num: Double) {
            bos.write(0) // Number marker
            val bits = java.lang.Double.doubleToRawLongBits(num)
            for (i in 7 downTo 0) {
                bos.write(((bits shr (i * 8)) and 0xFF).toInt())
            }
        }

        fun writeBoolean(bool: Boolean) {
            bos.write(1) // Boolean marker
            bos.write(if (bool) 1 else 0)
        }

        fun writeEcmaArray(properties: Map<String, Any>) {
            bos.write(8) // ECMA array marker
            val count = properties.size
            bos.write((count shr 24) and 0xFF)
            bos.write((count shr 16) and 0xFF)
            bos.write((count shr 8) and 0xFF)
            bos.write(count and 0xFF)

            for ((key, value) in properties) {
                // Key (UTF-8 string bytes without AMF type prefix)
                val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
                bos.write((keyBytes.size shr 8) and 0xFF)
                bos.write(keyBytes.size and 0xFF)
                bos.write(keyBytes)

                // Value (with type prefix)
                when (value) {
                    is Double -> writeNumber(value)
                    is Boolean -> writeBoolean(value)
                    is String -> writeString(value)
                }
            }
            
            // Object end marker: 0x00 0x00 0x09
            bos.write(0)
            bos.write(0)
            bos.write(9)
        }
    }
}
