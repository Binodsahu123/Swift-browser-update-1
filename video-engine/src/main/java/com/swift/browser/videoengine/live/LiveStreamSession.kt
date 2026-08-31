package com.swift.browser.videoengine.live

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.UUID

class LiveStreamSession(
    val context: Context,
    val config: LiveStreamConfig,
    val videoSource: VideoSource,
    val audioSource: AudioSource,
    val videoEncoder: VideoEncoder,
    val audioEncoder: AudioEncoder,
    val muxer: StreamMuxer,
    val transport: RtmpTransport,
    private val eventListener: ((LiveStreamEvent) -> Unit)? = null
) {
    companion object {
        private const val TAG = "LiveStreamSession"
    }

    val sessionId: String = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(LiveStreamState.IDLE)
    val state: StateFlow<LiveStreamState> = _state.asStateFlow()

    private val sessionScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var activeVideoSource: VideoSource = videoSource

    @Volatile
    private var activeAudioSource: AudioSource = audioSource

    private var watchdogJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastVideoFrameTimeMs = System.currentTimeMillis()
    @Volatile private var lastAudioFrameTimeMs = System.currentTimeMillis()
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 3
    private var isReconnecting = false
    private var totalAudioSamplesProcessed = 0L

    private fun launchWatchdog(): kotlinx.coroutines.Job {
        return sessionScope.launch {
            lastVideoFrameTimeMs = System.currentTimeMillis()
            lastAudioFrameTimeMs = System.currentTimeMillis()
            while (isActive) {
                kotlinx.coroutines.delay(1000)
                val currentState = _state.value
                if (currentState != LiveStreamState.STREAMING) {
                    continue
                }
                
                val now = System.currentTimeMillis()
                val timeSinceLastVideo = now - lastVideoFrameTimeMs
                val timeSinceLastAudio = now - lastAudioFrameTimeMs
                
                val isVideoDead = timeSinceLastVideo > 5000L
                val isAudioDead = timeSinceLastAudio > 5000L
                val stats = transport.getStats()
                val isTransportError = stats.rtmpState == "ERROR"
                val isQueueStalled = (muxer as? FlvStreamMuxer)?.getQueueSize()?.let { it > 50 } ?: false
                
                if (isVideoDead || isAudioDead || isTransportError || isQueueStalled) {
                    Log.e(TAG, "Watchdog triggered: isVideoDead=$isVideoDead, isAudioDead=$isAudioDead, isTransportError=$isTransportError, isQueueStalled=$isQueueStalled")
                    triggerReconnection()
                    break
                }
            }
        }
    }

    private fun triggerReconnection() {
        if (reconnectAttempt >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts reached ($maxReconnectAttempts). Failing.")
            updateState(LiveStreamState.FAILED, "Watchdog detected failures. Max retry attempts reached.")
            stop()
            return
        }

        reconnectAttempt++
        isReconnecting = true
        updateState(LiveStreamState.RECONNECTING, "Watchdog detected stream issue. Reconnecting (Attempt $reconnectAttempt/$maxReconnectAttempts)...")

        sessionScope.launch {
            val backoffMs = (1 shl reconnectAttempt) * 1000L // 2s, 4s, 8s...
            Log.i(TAG, "Reconnection backoff for $backoffMs ms before retrying connection")
            
            try {
                activeVideoSource.stopCapture()
                activeAudioSource.stopCapture()
                videoEncoder.stop()
                audioEncoder.stop()
                muxer.stop()
                transport.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping pipeline during reconnect: ${e.message}")
            }
            
            kotlinx.coroutines.delay(backoffMs)
            
            try {
                Log.i(TAG, "Attempting RTMP reconnect (attempt $reconnectAttempt)")
                val connected = transport.connect(config.streamUrl, config.streamKey)
                if (connected) {
                    Log.i(TAG, "Reconnect RTMP connection successful!")
                    
                    videoEncoder.configure(config)
                    videoEncoder.start()
                    val newSurface = videoEncoder.getInputSurface()
                    if (newSurface != null) {
                        activeVideoSource.setOutputSurface(newSurface)
                    }
                    
                    audioEncoder.configure(config)
                    audioEncoder.start()
                    totalAudioSamplesProcessed = 0L
                    
                    muxer.configure(config)
                    muxer.start()
                    
                    activeVideoSource.startCapture(config)
                    activeAudioSource.startCapture(config)
                    
                    lastVideoFrameTimeMs = System.currentTimeMillis()
                    lastAudioFrameTimeMs = System.currentTimeMillis()
                    isReconnecting = false
                    
                    updateState(LiveStreamState.STREAMING)
                    watchdogJob?.cancel()
                    watchdogJob = launchWatchdog()
                } else {
                    Log.e(TAG, "Reconnect RTMP connection failed.")
                    triggerReconnection()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during reconnect: ${e.message}")
                triggerReconnection()
            }
        }
    }

    fun start() {
        synchronized(this) {
            val currentState = _state.value
            if (currentState != LiveStreamState.IDLE && currentState != LiveStreamState.STOPPED) {
                Log.w(TAG, "Cannot start session: Current state is $currentState (already active)")
                return
            }
            updateState(LiveStreamState.PREPARING)
        }

        sessionScope.launch {
            try {
                Log.i(TAG, "Starting live streaming session $sessionId with config: $config")

                // Step 1: Initialize Audio and Video Encoders & Muxer
                updateState(LiveStreamState.INITIALIZING_VIDEO)
                videoEncoder.configure(config)
                videoEncoder.start() // Start encoder FIRST so it creates the input surface!
                val encoderSurface = videoEncoder.getInputSurface()
                if (encoderSurface == null) {
                    throw IllegalStateException("Video encoder did not provide an input surface")
                }
                activeVideoSource.setOutputSurface(encoderSurface)

                updateState(LiveStreamState.INITIALIZING_AUDIO)
                audioEncoder.configure(config)
                muxer.configure(config)

                // Wire callbacks
                videoEncoder.setEncodedVideoListener { buffer, info ->
                    lastVideoFrameTimeMs = System.currentTimeMillis()
                    val currentState = _state.value
                    if (currentState == LiveStreamState.STREAMING || currentState == LiveStreamState.ENCODING) {
                        muxer.writeVideoSample(buffer, info)
                    }
                }

                audioEncoder.setEncodedAudioListener { buffer, info ->
                    lastAudioFrameTimeMs = System.currentTimeMillis()
                    val currentState = _state.value
                    if (currentState == LiveStreamState.STREAMING || currentState == LiveStreamState.ENCODING) {
                        muxer.writeAudioSample(buffer, info)
                    }
                }

                totalAudioSamplesProcessed = 0L // Reset sample count at startup
                activeAudioSource.setAudioDataListener { data, size ->
                    val currentState = _state.value
                    if (currentState == LiveStreamState.STREAMING || currentState == LiveStreamState.ENCODING) {
                        // Feed raw PCM audio frames into encoder with drift-free PTS based on sample count
                        val channels = config.audioChannels
                        val sampleRate = config.audioSampleRate
                        val bytesPerSample = 2 // 16-bit PCM
                        val samples = size / (channels * bytesPerSample)
                        
                        val presentationTimeUs = (totalAudioSamplesProcessed * 1_000_000L) / sampleRate
                        totalAudioSamplesProcessed += samples
                        
                        audioEncoder.queueInputBuffer(data, size, presentationTimeUs)
                    }
                }

                muxer.setRtmpPacketListener { packet ->
                    val currentState = _state.value
                    if (currentState == LiveStreamState.STREAMING || currentState == LiveStreamState.ENCODING) {
                        transport.sendRtmpPacket(packet)
                    }
                }

                // Step 2: Establish connection to RTMP/RTMPS endpoint
                updateState(LiveStreamState.CONNECTING)
                val connected = transport.connect(config.streamUrl, config.streamKey)
                if (!connected) {
                    throw IllegalStateException("RTMP connection to endpoint failed")
                }

                // Step 3: Start pipeline components
                updateState(LiveStreamState.ENCODING)
                // videoEncoder is already started!
                audioEncoder.start()
                muxer.start()

                // Start capture
                activeVideoSource.startCapture(config)
                activeAudioSource.startCapture(config)

                updateState(LiveStreamState.STREAMING)
                Log.i(TAG, "Session $sessionId is now active and streaming.")

                // Launch Watchdog
                watchdogJob?.cancel()
                watchdogJob = launchWatchdog()

            } catch (e: Exception) {
                Log.e(TAG, "Error during live stream session initialization", e)
                handleFailure(e)
            }
        }
    }

    fun switchVideoSource(newVideoSource: VideoSource): Boolean {
        synchronized(this) {
            val currentState = _state.value
            if (currentState != LiveStreamState.STREAMING && currentState != LiveStreamState.ENCODING) {
                Log.w(TAG, "Cannot switch video source: state is $currentState")
                return false
            }

            Log.i(TAG, "Switching active video source dynamically")
            val encoderSurface = videoEncoder.getInputSurface()
            if (encoderSurface == null) {
                Log.e(TAG, "Encoder surface is unavailable for source switch")
                return false
            }

            try {
                activeVideoSource.stopCapture()
                activeVideoSource.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping active video source during switch: ${e.message}")
            }

            activeVideoSource = newVideoSource
            activeVideoSource.setOutputSurface(encoderSurface)
            activeVideoSource.startCapture(config)
            return true
        }
    }

    fun switchAudioSource(newAudioSource: AudioSource): Boolean {
        synchronized(this) {
            val currentState = _state.value
            if (currentState != LiveStreamState.STREAMING && currentState != LiveStreamState.ENCODING) {
                Log.w(TAG, "Cannot switch audio source: state is $currentState")
                return false
            }

            Log.i(TAG, "Switching active audio source dynamically")
            try {
                activeAudioSource.stopCapture()
                activeAudioSource.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping active audio source during switch: ${e.message}")
            }

            activeAudioSource = newAudioSource
            activeAudioSource.setAudioDataListener { data, size ->
                val stateNow = _state.value
                if (stateNow == LiveStreamState.STREAMING || stateNow == LiveStreamState.ENCODING) {
                    val presentationTimeUs = System.nanoTime() / 1000L
                    audioEncoder.queueInputBuffer(data, size, presentationTimeUs)
                }
            }
            activeAudioSource.startCapture(config)
            return true
        }
    }

    fun stop() {
        synchronized(this) {
            val currentState = _state.value
            if (currentState == LiveStreamState.STOPPED || currentState == LiveStreamState.STOPPING || currentState == LiveStreamState.IDLE) {
                Log.d(TAG, "Stop requested, but session is already in state: $currentState")
                return
            }
            updateState(LiveStreamState.STOPPING)
        }

        watchdogJob?.cancel()
        watchdogJob = null
        reconnectAttempt = 0
        isReconnecting = false

        sessionScope.launch {
            try {
                Log.i(TAG, "Stopping live streaming session $sessionId")

                // Stop capture sources
                try { activeVideoSource.stopCapture() } catch (e: Exception) { Log.e(TAG, "Error stopping video source", e) }
                try { activeAudioSource.stopCapture() } catch (e: Exception) { Log.e(TAG, "Error stopping audio source", e) }

                // Stop encoders and muxer
                try { videoEncoder.stop() } catch (e: Exception) { Log.e(TAG, "Error stopping video encoder", e) }
                try { audioEncoder.stop() } catch (e: Exception) { Log.e(TAG, "Error stopping audio encoder", e) }
                try { muxer.stop() } catch (e: Exception) { Log.e(TAG, "Error stopping muxer", e) }

                // Disconnect RTMP transport
                try { transport.disconnect() } catch (e: Exception) { Log.e(TAG, "Error disconnecting transport", e) }

                // Release components
                releaseComponents()

                updateState(LiveStreamState.STOPPED)
                Log.i(TAG, "Live streaming session $sessionId stopped successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during stop operation", e)
                updateState(LiveStreamState.FAILED)
            }
        }
    }

    private fun releaseComponents() {
        try { activeVideoSource.release() } catch (e: Exception) { Log.e(TAG, "Error releasing video source", e) }
        try { activeAudioSource.release() } catch (e: Exception) { Log.e(TAG, "Error releasing audio source", e) }
        try { videoEncoder.release() } catch (e: Exception) { Log.e(TAG, "Error releasing video encoder", e) }
        try { audioEncoder.release() } catch (e: Exception) { Log.e(TAG, "Error releasing audio encoder", e) }
        try { muxer.release() } catch (e: Exception) { Log.e(TAG, "Error releasing muxer", e) }
        try { transport.release() } catch (e: Exception) { Log.e(TAG, "Error releasing transport", e) }
    }

    private fun handleFailure(e: Exception) {
        updateState(LiveStreamState.FAILED, e.message)
        eventListener?.invoke(LiveStreamEvent.ErrorOccurred("STREAM_INIT_FAILED", e.message ?: "Unknown error", e))
        releaseComponents()
    }

    private fun updateState(newState: LiveStreamState, message: String? = null) {
        _state.value = newState
        eventListener?.invoke(LiveStreamEvent.StateChanged(newState, message))
    }

    fun getStats(): LiveStreamStats {
        return transport.getStats()
    }
}
