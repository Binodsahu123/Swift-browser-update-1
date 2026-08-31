package com.swift.browser.videoengine.live

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LiveStreamingEngine {
    private const val TAG = "LiveStreamingEngine"

    private var activeSession: LiveStreamSession? = null
    private val stateListeners = mutableListOf<(LiveStreamState) -> Unit>()

    private val _engineState = MutableStateFlow(LiveStreamState.IDLE)
    val engineState: StateFlow<LiveStreamState> = _engineState.asStateFlow()

    @Synchronized
    fun startStream(
        context: Context,
        config: LiveStreamConfig,
        videoSourceType: String = "CAMERA", // "CAMERA" or "SCREEN"
        audioSourceType: String = "MICROPHONE", // "MICROPHONE" or "NO_AUDIO"
        mediaProjection: android.media.projection.MediaProjection? = null
    ): LiveStreamSession {
        Log.i(TAG, "startStream requested with video=$videoSourceType, audio=$audioSourceType")

        val videoSource = when (videoSourceType.uppercase()) {
            "SCREEN" -> {
                if (mediaProjection == null) {
                    throw IllegalArgumentException("MediaProjection is required for SCREEN video source type")
                }
                ScreenVideoSource(context, mediaProjection)
            }
            "CAMERA" -> {
                CameraVideoSource(context)
            }
            else -> {
                throw IllegalArgumentException("Unsupported video source type: $videoSourceType")
            }
        }

        val audioSource = when (audioSourceType.uppercase()) {
            "MICROPHONE" -> {
                MicrophoneAudioSource(context)
            }
            "NO_AUDIO" -> {
                SilenceAudioSource()
            }
            else -> {
                throw IllegalArgumentException("Unsupported audio source type: $audioSourceType")
            }
        }

        val videoEncoder = H264VideoEncoder()
        val audioEncoder = AacAudioEncoder()
        val muxer = FlvStreamMuxer()
        val transport = RtmpTransportImpl()

        return startSession(
            context = context,
            config = config,
            videoSource = videoSource,
            audioSource = audioSource,
            videoEncoder = videoEncoder,
            audioEncoder = audioEncoder,
            muxer = muxer,
            transport = transport
        )
    }

    @Synchronized
    fun stopStream() {
        Log.i(TAG, "stopStream requested")
        stopSession()
    }

    @Synchronized
    fun switchSource(
        context: Context,
        videoSourceType: String? = null,
        audioSourceType: String? = null,
        mediaProjection: android.media.projection.MediaProjection? = null
    ): Boolean {
        val session = activeSession ?: run {
            Log.w(TAG, "switchSource: No active session running")
            return false
        }

        var success = true

        if (videoSourceType != null) {
            val newVideoSource = when (videoSourceType.uppercase()) {
                "SCREEN" -> {
                    if (mediaProjection == null) {
                        Log.e(TAG, "MediaProjection is required for dynamic SCREEN video switch")
                        return false
                    }
                    ScreenVideoSource(context, mediaProjection)
                }
                "CAMERA" -> {
                    CameraVideoSource(context)
                }
                else -> {
                    Log.e(TAG, "Unsupported video switch type: $videoSourceType")
                    return false
                }
            }
            success = session.switchVideoSource(newVideoSource) && success
        }

        if (audioSourceType != null) {
            val newAudioSource = when (audioSourceType.uppercase()) {
                "MICROPHONE" -> {
                    MicrophoneAudioSource(context)
                }
                "NO_AUDIO" -> {
                    SilenceAudioSource()
                }
                else -> {
                    Log.e(TAG, "Unsupported audio switch type: $audioSourceType")
                    return false
                }
            }
            success = session.switchAudioSource(newAudioSource) && success
        }

        return success
    }

    @Synchronized
    fun getStreamStats(): LiveStreamStats? {
        return activeSession?.getStats()
    }

    @Synchronized
    fun shutdown() {
        Log.i(TAG, "shutdown requested")
        stopStream()
    }

    @Synchronized
    fun startSession(
        context: Context,
        config: LiveStreamConfig,
        videoSource: VideoSource,
        audioSource: AudioSource,
        videoEncoder: VideoEncoder,
        audioEncoder: AudioEncoder,
        muxer: StreamMuxer,
        transport: RtmpTransport
    ): LiveStreamSession {
        if (activeSession != null) {
            val state = activeSession!!.state.value
            if (state != LiveStreamState.STOPPED && state != LiveStreamState.FAILED) {
                Log.w(TAG, "Cannot start a new live streaming session. Active session ${activeSession!!.sessionId} is already running in state $state")
                return activeSession!!
            }
        }

        Log.i(TAG, "Initializing a new Direct Live Streaming Session")
        
        val session = LiveStreamSession(
            context = context.applicationContext,
            config = config,
            videoSource = videoSource,
            audioSource = audioSource,
            videoEncoder = videoEncoder,
            audioEncoder = audioEncoder,
            muxer = muxer,
            transport = transport,
            eventListener = { event ->
                handleSessionEvent(event)
            }
        )

        activeSession = session
        
        // Start Foreground Service to host streaming lifecycle safely
        LiveStreamForegroundService.startService(context.applicationContext)
        
        session.start()
        return session
    }

    @Synchronized
    fun stopSession() {
        val session = activeSession
        if (session == null) {
            Log.d(TAG, "No active session to stop")
            return
        }
        
        session.stop()
        activeSession = null
        // Securely wipe sensitive keys from memory cache when streaming ends
        LiveStreamCredentialStore.wipeMemory()
    }

    fun getActiveSession(): LiveStreamSession? {
        return activeSession
    }

    @Synchronized
    fun registerStateListener(listener: (LiveStreamState) -> Unit) {
        stateListeners.add(listener)
        // Emit current state immediately
        listener(_engineState.value)
    }

    @Synchronized
    fun unregisterStateListener(listener: (LiveStreamState) -> Unit) {
        stateListeners.remove(listener)
    }

    private fun handleSessionEvent(event: LiveStreamEvent) {
        when (event) {
            is LiveStreamEvent.StateChanged -> {
                Log.d(TAG, "Session state transition: ${event.state}")
                _engineState.value = event.state
                
                // Notify listeners
                val listeners = synchronized(this) { stateListeners.toList() }
                listeners.forEach { it(event.state) }
            }
            is LiveStreamEvent.ErrorOccurred -> {
                Log.e(TAG, "Session Error [${event.errorCode}]: ${event.message}", event.exception)
            }
            is LiveStreamEvent.WarningOccurred -> {
                Log.w(TAG, "Session Warning [${event.warningCode}]: ${event.message}")
            }
            is LiveStreamEvent.StatsUpdated -> {
                Log.v(TAG, "Stats updated: ${event.stats}")
            }
        }
    }

    // --- Lifecycle handling routing ---

    fun onActivityPaused() {
        Log.i(TAG, "Lifecycle Hook: onActivityPaused")
    }

    fun onActivityResume() {
        Log.i(TAG, "Lifecycle Hook: onActivityResume")
    }

    fun onScreenLocked() {
        Log.i(TAG, "Lifecycle Hook: onScreenLocked")
    }

    fun onBackground() {
        Log.i(TAG, "Lifecycle Hook: onBackground")
    }

    fun onForeground() {
        Log.i(TAG, "Lifecycle Hook: onForeground")
    }

    fun onServiceDestruction() {
        Log.i(TAG, "Lifecycle Hook: onServiceDestruction - guarding leaks by stopping active sessions")
        stopSession()
    }

    fun onNetworkLoss() {
        Log.w(TAG, "Lifecycle Hook: Network loss detected! Attempting automatic recovery.")
        val session = activeSession
        if (session != null && session.state.value == LiveStreamState.STREAMING) {
            handleSessionEvent(LiveStreamEvent.StateChanged(LiveStreamState.RECONNECTING, "Network connection lost. Reconnecting..."))
        }
    }
}
