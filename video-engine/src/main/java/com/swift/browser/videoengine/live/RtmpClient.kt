package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

open class RtmpClient(
    private val rtmpUrlStr: String,
    private val timeoutMs: Int = 10000
) {
    enum class State {
        DISCONNECTED,
        CONNECTING,
        PUBLISHING,
        ERROR
    }

    interface Listener {
        fun onStateChanged(state: State)
        fun onError(e: Exception)
    }

    private var state = State.DISCONNECTED
    private var listener: Listener? = null
    
    private var connection: RtmpConnection? = null
    private val commandSession = RtmpCommandSession()
    private val isRunning = AtomicBoolean(false)
    private var readerThread: Thread? = null

    private var activeStreamId = 1 // Default stream ID
    private val publishLatch = CountDownLatch(1)
    private var publishError: Exception? = null

    @Volatile
    private var bytesSent = 0L

    private var peerWindowAckSize = 2500000
    private var lastAckSentBytes = 0L
    private var peerBandwidth = 2500000
    private var peerBandwidthLimitType = 2 // Dynamic

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun getState(): State = state

    private fun changeState(newState: State) {
        state = newState
        listener?.onStateChanged(newState)
    }

    /**
     * Connects, handshakes, negotiates RTMP session, and starts publishing stream.
     * Blocks until publish begins or fails due to timeout or error.
     */
    @Synchronized
    fun startPublishing() {
        if (state == State.CONNECTING || state == State.PUBLISHING) {
            return
        }

        changeState(State.CONNECTING)
        isRunning.set(true)

        try {
            val rtmpUrl = RtmpUrlParser.parse(rtmpUrlStr)
            val conn = RtmpConnection(rtmpUrl.host, rtmpUrl.port, timeoutMs, rtmpUrl.isSecure)
            conn.connect()
            connection = conn

            // Set up transaction listeners BEFORE starting reader thread to avoid race conditions
            val connectTid = 1.0
            val createStreamTid = commandSession.nextTransactionId()

            val sessionLatch = CountDownLatch(1)
            var sessionError: Exception? = null

            commandSession.registerTransaction(connectTid) { info, result ->
                // Connect result received
                val createStreamMsg = commandSession.buildCreateStream(createStreamTid)
                try {
                    conn.writeMessage(createStreamMsg)
                } catch (e: Exception) {
                    sessionError = e
                    sessionLatch.countDown()
                }
            }

            commandSession.registerTransaction(createStreamTid) { info, result ->
                // CreateStream result received (result holds streamId, typically 1)
                val streamId = when (result) {
                    is Amf0Value.Number -> result.value.toInt()
                    else -> 1
                }
                activeStreamId = streamId

                val publishMsg = commandSession.buildPublish(rtmpUrl.streamKey, activeStreamId, commandSession.nextTransactionId())
                try {
                    conn.writeMessage(publishMsg)
                } catch (e: Exception) {
                    sessionError = e
                    sessionLatch.countDown()
                }
            }

            // Start reader thread to process handshake response & incoming control packets
            startReaderThread()

            // 1. Send the Connect Message
            // We strip any credentials / streamkey from tcUrl for security
            val connectMsg = commandSession.buildConnect(rtmpUrl.tcUrl, rtmpUrl.appName)
            conn.writeMessage(connectMsg)

            // Wait for onStatus or error
            val success = publishLatch.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            if (!success) {
                val err = sessionError ?: publishError ?: IOException("RTMP negotiation timed out")
                throw err
            }

            val finalErr = publishError
            if (finalErr != null) {
                throw finalErr
            }

            changeState(State.PUBLISHING)

        } catch (e: Exception) {
            handleFailure(e)
            throw e
        }
    }

    private fun startReaderThread() {
        val thread = Thread {
            val conn = connection ?: return@Thread
            while (isRunning.get()) {
                try {
                    val msg = conn.readMessage()
                    handleIncomingMessage(msg)
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        handleFailure(e)
                    }
                    break
                }
            }
        }
        thread.name = "rtmp-reader"
        thread.isDaemon = true
        readerThread = thread
        thread.start()
    }

    private fun handleIncomingMessage(message: RtmpMessage) {
        val conn = connection ?: return
        when (message.type) {
            1 -> {
                // Set Chunk Size (4 bytes)
                if (message.payload.size >= 4) {
                    val newSize = ((message.payload[0].toInt() and 0xFF) shl 24) or
                            ((message.payload[1].toInt() and 0xFF) shl 16) or
                            ((message.payload[2].toInt() and 0xFF) shl 8) or
                            (message.payload[3].toInt() and 0xFF)
                    conn.setReadChunkSize(newSize)
                }
            }
            3 -> {
                // Acknowledgement (4 bytes sequence number)
                if (message.payload.size >= 4) {
                    val sequenceNumber = ((message.payload[0].toLong() and 0xFFL) shl 24) or
                            ((message.payload[1].toLong() and 0xFFL) shl 16) or
                            ((message.payload[2].toLong() and 0xFFL) shl 8) or
                            (message.payload[3].toLong() and 0xFFL)
                }
            }
            5 -> {
                // Window Acknowledgement Size (4 bytes)
                if (message.payload.size >= 4) {
                    val ackSize = ((message.payload[0].toInt() and 0xFF) shl 24) or
                            ((message.payload[1].toInt() and 0xFF) shl 16) or
                            ((message.payload[2].toInt() and 0xFF) shl 8) or
                            (message.payload[3].toInt() and 0xFF)
                    peerWindowAckSize = ackSize
                    sendAcknowledgementSize(ackSize)
                }
            }
            6 -> {
                // Set Peer Bandwidth (5 bytes: 4 bytes window size + 1 byte limit type)
                if (message.payload.size >= 5) {
                    val windowSize = ((message.payload[0].toInt() and 0xFF) shl 24) or
                            ((message.payload[1].toInt() and 0xFF) shl 16) or
                            ((message.payload[2].toInt() and 0xFF) shl 8) or
                            (message.payload[3].toInt() and 0xFF)
                    val limitType = message.payload[4].toInt() and 0xFF
                    peerBandwidth = windowSize
                    peerBandwidthLimitType = limitType
                    sendAcknowledgementSize(windowSize)
                }
            }
            20 -> {
                // AMF0 Command Message
                val result = commandSession.handleCommandResponse(message)
                if (result is RtmpCommandSession.CommandResult.Status) {
                    if (result.code == "NetStream.Publish.Start" || result.code == "NetStream.Publish.Local") {
                        publishLatch.countDown()
                    } else if (result.level == "error") {
                        publishError = IOException("Publish failed: ${result.code}")
                        publishLatch.countDown()
                    }
                }
            }
        }

        // Check window acknowledgement threshold
        val bytesRead = conn.getTotalBytesRead()
        if (bytesRead - lastAckSentBytes >= peerWindowAckSize && peerWindowAckSize > 0) {
            sendAcknowledgement(bytesRead)
            lastAckSentBytes = bytesRead
        }
    }

    private fun sendAcknowledgement(sequenceNumber: Long) {
        val conn = connection ?: return
        val payload = ByteArray(4)
        val seq = sequenceNumber.toInt()
        payload[0] = ((seq shr 24) and 0xFF).toByte()
        payload[1] = ((seq shr 16) and 0xFF).toByte()
        payload[2] = ((seq shr 8) and 0xFF).toByte()
        payload[3] = (seq and 0xFF).toByte()

        val msg = RtmpMessage(
            type = 3, // Acknowledgement
            chunkStreamId = 2,
            messageStreamId = 0,
            timestamp = 0L,
            payload = payload
        )
        try {
            conn.writeMessage(msg)
        } catch (_: Exception) {}
    }

    private fun sendAcknowledgementSize(size: Int) {
        val conn = connection ?: return
        val payload = ByteArray(4)
        payload[0] = ((size shr 24) and 0xFF).toByte()
        payload[1] = ((size shr 16) and 0xFF).toByte()
        payload[2] = ((size shr 8) and 0xFF).toByte()
        payload[3] = (size and 0xFF).toByte()

        val msg = RtmpMessage(
            type = 5, // Window Acknowledgement Size
            chunkStreamId = 2,
            messageStreamId = 0,
            timestamp = 0L,
            payload = payload
        )
        try {
            conn.writeMessage(msg)
        } catch (_: Exception) {}
    }

    fun sendAudio(timestampMs: Long, payload: ByteArray) {
        val conn = connection ?: return
        if (state != State.PUBLISHING) return

        val msg = RtmpMessage(
            type = 8, // Audio
            chunkStreamId = 4,
            messageStreamId = activeStreamId,
            timestamp = timestampMs,
            payload = payload
        )
        conn.writeMessage(msg)
        bytesSent += payload.size
    }

    fun sendVideo(timestampMs: Long, payload: ByteArray) {
        val conn = connection ?: return
        if (state != State.PUBLISHING) return

        val msg = RtmpMessage(
            type = 9, // Video
            chunkStreamId = 6,
            messageStreamId = activeStreamId,
            timestamp = timestampMs,
            payload = payload
        )
        conn.writeMessage(msg)
        bytesSent += payload.size
    }

    fun sendMetadata(metadataPayload: ByteArray) {
        val conn = connection ?: return
        if (state != State.PUBLISHING) return

        val msg = RtmpMessage(
            type = 18, // AMF0 Data/Metadata
            chunkStreamId = 3,
            messageStreamId = activeStreamId,
            timestamp = 0L,
            payload = metadataPayload
        )
        conn.writeMessage(msg)
    }

    private fun handleFailure(e: Exception) {
        isRunning.set(false)
        connection?.close()
        publishError = e
        publishLatch.countDown()
        changeState(State.ERROR)
        listener?.onError(e)
    }

    @Synchronized
    fun close() {
        isRunning.set(false)
        connection?.close()
        try {
            readerThread?.interrupt()
        } catch (_: Exception) {}
        readerThread = null
        changeState(State.DISCONNECTED)
    }
}
