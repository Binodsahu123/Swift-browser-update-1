package com.swift.browser.videoengine.live

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class RtmpStreamTransport(
    private val rtmpUrl: String,
    private val maxQueueSize: Int = 120,
    private val maxReconnectAttempts: Int = 5
) {
    enum class State {
        IDLE,
        CONNECTING,
        PUBLISHING,
        RECONNECTING,
        ERROR,
        STOPPED
    }

    interface Listener {
        fun onTransportStateChanged(state: State)
        fun onTransportError(e: Exception)
        fun onPacketDropped(packetType: Int, isKeyframe: Boolean)
    }

    private var state = State.IDLE
    private var listener: Listener? = null

    private val queue = LinkedBlockingQueue<RtmpPacket>(maxQueueSize)
    private var rtmpClient: RtmpClient? = null
    private val isRunning = AtomicBoolean(false)
    
    private var senderThread: Thread? = null
    private var reconnectAttempt = 0
    private val isReconnecting = AtomicBoolean(false)

    // Stats
    private var packetsSent = 0L
    private var packetsDropped = 0L
    private var totalBytesSent = 0L
    private var lastRttMs = 0L

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun getState(): State = state

    private fun changeState(newState: State) {
        state = newState
        listener?.onTransportStateChanged(newState)
    }

    /**
     * Helper to sanitize and mask credentials / streamkey from RTMP URL logs
     */
    fun sanitizeUrl(url: String): String {
        return try {
            val parsed = RtmpUrlParser.parse(url)
            "rtmp://${parsed.host}:${parsed.port}/${parsed.appName}/[REDACTED_STREAM_KEY]"
        } catch (_: Exception) {
            "rtmp://[REDACTED_HOST_AND_PATH]"
        }
    }

    /**
     * Starts the transport and begins the connection process and send loop.
     */
    @Synchronized
    fun start() {
        if (state == State.CONNECTING || state == State.PUBLISHING || state == State.RECONNECTING) {
            return
        }

        isRunning.set(true)
        reconnectAttempt = 0
        queue.clear()
        
        startSenderThread()
    }

    private fun startSenderThread() {
        val thread = Thread {
            changeState(State.CONNECTING)

            while (isRunning.get()) {
                try {
                    // Initialize Client
                    val client = RtmpClient(rtmpUrl)
                    rtmpClient = client
                    
                    client.setListener(object : RtmpClient.Listener {
                        override fun onStateChanged(clientState: RtmpClient.State) {
                            if (clientState == RtmpClient.State.PUBLISHING) {
                                reconnectAttempt = 0
                                isReconnecting.set(false)
                                changeState(State.PUBLISHING)
                            }
                        }

                        override fun onError(e: Exception) {
                            // Handled inside the main sendLoop thread
                        }
                    })

                    // Establish RTMP publishing session (blocks until done or exception thrown)
                    val sendStart = System.currentTimeMillis()
                    client.startPublishing()
                    lastRttMs = System.currentTimeMillis() - sendStart

                    // Main sending loop
                    while (isRunning.get() && client.getState() == RtmpClient.State.PUBLISHING) {
                        val packet = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                        
                        try {
                            when (packet.type) {
                                8 -> client.sendAudio(packet.timestamp, packet.payload)
                                9 -> client.sendVideo(packet.timestamp, packet.payload)
                                18 -> client.sendMetadata(packet.payload)
                            }
                            packetsSent++
                            totalBytesSent += packet.payload.size
                        } catch (e: IOException) {
                            // Stream write failure (e.g. server disconnect, network loss)
                            throw e
                        }
                    }

                } catch (e: Exception) {
                    if (!isRunning.get()) break
                    
                    // Connection lost or handshake failed
                    listener?.onTransportError(e)
                    
                    if (reconnectAttempt < maxReconnectAttempts) {
                        reconnectAttempt++
                        isReconnecting.set(true)
                        changeState(State.RECONNECTING)

                        // Exponential Backoff: 2s, 4s, 8s, 16s...
                        val delay = minOf(16000L, 1000L * (1 shl reconnectAttempt))
                        try {
                            Thread.sleep(delay)
                        } catch (_: InterruptedException) {
                            break
                        }
                    } else {
                        changeState(State.ERROR)
                        isRunning.set(false)
                        break
                    }
                } finally {
                    rtmpClient?.close()
                    rtmpClient = null
                }
            }

            if (!isRunning.get() && state != State.ERROR) {
                changeState(State.STOPPED)
            }
        }
        
        thread.name = "rtmp-transport-sender"
        thread.isDaemon = true
        senderThread = thread
        thread.start()
    }

    /**
     * Enqueues an already-muxed RtmpPacket from FlvMuxer.
     * Implements active backpressure dropping of non-keyframes if bounded queue is full.
     * Mandatory codec configuration (metadata, sequence headers) is NEVER dropped.
     */
    fun feedPacket(packet: RtmpPacket) {
        if (state == State.STOPPED) return

        val isMandatory = packet.type == 18 || isSequenceHeader(packet)

        // Check if queue has space
        if (queue.size >= maxQueueSize) {
            if (isMandatory) {
                // Mandatory packets MUST be enqueued. Evict non-keyframe video or audio from queue to make space.
                val iterator = queue.iterator()
                var evicted = false
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    if (!item.isKeyframe && item.type != 18 && !isSequenceHeader(item)) {
                        iterator.remove()
                        packetsDropped++
                        listener?.onPacketDropped(item.type, item.isKeyframe)
                        evicted = true
                        break
                    }
                }
                if (!evicted) {
                    // Force poll oldest element if queue is completely full of keyframes
                    val old = queue.poll()
                    if (old != null) {
                        packetsDropped++
                        listener?.onPacketDropped(old.type, old.isKeyframe)
                    }
                }
            } else if (!packet.isKeyframe) {
                // Buffer Overflow Backpressure: Drop non-keyframe immediately
                packetsDropped++
                listener?.onPacketDropped(packet.type, packet.isKeyframe)
                return
            } else {
                // Find and remove oldest non-keyframe to make space for keyframe
                val iterator = queue.iterator()
                var removed = false
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    if (!item.isKeyframe && item.type != 18 && !isSequenceHeader(item)) {
                        iterator.remove()
                        packetsDropped++
                        listener?.onPacketDropped(item.type, item.isKeyframe)
                        removed = true
                        break
                    }
                }
                
                if (!removed || queue.size >= maxQueueSize) {
                    val old = queue.poll()
                    if (old != null) {
                        packetsDropped++
                        listener?.onPacketDropped(old.type, old.isKeyframe)
                    }
                }
            }
        }

        queue.offer(packet)
    }

    private fun isSequenceHeader(packet: RtmpPacket): Boolean {
        if (packet.type == 9 && packet.payload.size >= 2) {
            // AVC sequence header: (payload[0] & 0x0F) == 7 (AVC) and payload[1] == 0 (AVCDecoderConfigurationRecord)
            return (packet.payload[0].toInt() and 0x0F) == 7 && packet.payload[1].toInt() == 0
        }
        if (packet.type == 8 && packet.payload.size >= 2) {
            // AAC sequence header: (payload[0] >> 4) == 10 (AAC) and payload[1] == 0 (AACSequenceHeader)
            return ((packet.payload[0].toInt() and 0xF0) shr 4) == 10 && packet.payload[1].toInt() == 0
        }
        return false
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        changeState(State.STOPPED)
        
        rtmpClient?.close()
        rtmpClient = null
        
        try {
            senderThread?.interrupt()
        } catch (_: Exception) {}
        senderThread = null
        
        queue.clear()
    }

    fun getStats(): TransportStats {
        return TransportStats(packetsSent, packetsDropped, queue.size, totalBytesSent, lastRttMs)
    }

    data class TransportStats(
        val sent: Long,
        val dropped: Long,
        val queueSize: Int,
        val bytesSent: Long = 0L,
        val rttMs: Long = 0L
    )
}
