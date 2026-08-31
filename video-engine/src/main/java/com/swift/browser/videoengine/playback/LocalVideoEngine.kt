package com.swift.browser.videoengine.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.VideoView
import com.swift.browser.videoengine.core.PlaybackStatus
import com.swift.browser.videoengine.core.VideoPlaybackState
import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LocalVideoEngine private constructor(private val context: Context) {

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val commandMutex = Mutex()

    private var activeVideoView: VideoView? = null
    private var activeMediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(VideoPlaybackState())
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var positionJob: kotlinx.coroutines.Job? = null

    var onCompletionListener: (() -> Unit)? = null

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = engineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                if (_isPlaying.value) {
                    val pos = activeVideoView?.currentPosition?.toLong()
                        ?: activeMediaPlayer?.currentPosition?.toLong()
                    if (pos != null && pos >= 0) {
                        _positionMs.value = pos
                        _playbackState.value = _playbackState.value.copy(positionMs = pos)
                    }
                }
            }
        }
    }

    private fun stopPositionTicker() {
        positionJob?.cancel()
        positionJob = null
    }

    fun attachVideoView(videoView: VideoView) {
        activeVideoView = videoView
        _currentVideo.value?.let { video ->
            setupVideoViewAndPlay(videoView, video, _isPlaying.value)
        }
    }

    fun detachVideoView(videoView: VideoView) {
        if (activeVideoView == videoView) {
            activeVideoView = null
        }
    }

    fun loadAndPlay(video: VideoItem, autoPlay: Boolean = true) {
        engineScope.launch {
            commandMutex.withLock {
                _currentVideo.value = video
                _positionMs.value = 0L
                _durationMs.value = video.duration ?: 0L
                _playbackState.value = VideoPlaybackState(PlaybackStatus.PREPARING)
                _errorMessage.value = null

                activeVideoView?.let { videoView ->
                    setupVideoViewAndPlay(videoView, video, autoPlay)
                } ?: run {
                    _isPlaying.value = autoPlay
                    if (autoPlay) {
                        _playbackState.value = VideoPlaybackState(PlaybackStatus.PLAYING)
                        startPositionTicker()
                    }
                }
            }
        }
    }

    private fun setupVideoViewAndPlay(videoView: VideoView, video: VideoItem, autoPlay: Boolean) {
        try {
            val uri = when {
                video.path.startsWith("content://") || video.path.startsWith("http://") || video.path.startsWith("https://") -> Uri.parse(video.path)
                else -> Uri.fromFile(File(video.path))
            }
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { mp ->
                activeMediaPlayer = mp
                _durationMs.value = mp.duration.toLong()
                _playbackState.value = VideoPlaybackState(PlaybackStatus.READY, 0L, _durationMs.value)
                applySpeedAndVolume(mp)

                if (autoPlay) {
                    mp.start()
                    _isPlaying.value = true
                    _playbackState.value = VideoPlaybackState(PlaybackStatus.PLAYING, mp.currentPosition.toLong(), _durationMs.value)
                    startPositionTicker()
                }
            }
            videoView.setOnCompletionListener {
                _isPlaying.value = false
                stopPositionTicker()
                _playbackState.value = VideoPlaybackState(PlaybackStatus.COMPLETED, _durationMs.value, _durationMs.value)
                onCompletionListener?.invoke()
            }
            videoView.setOnErrorListener { _, what, extra ->
                val err = "Playback error (what: $what, extra: $extra)"
                Log.e(TAG, err)
                _errorMessage.value = err
                _isPlaying.value = false
                stopPositionTicker()
                _playbackState.value = VideoPlaybackState(PlaybackStatus.ERROR)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up video playback", e)
            _errorMessage.value = e.message ?: "Playback initialization failed"
            _playbackState.value = VideoPlaybackState(PlaybackStatus.ERROR)
        }
    }

    fun play() {
        engineScope.launch {
            commandMutex.withLock {
                activeVideoView?.start() ?: activeMediaPlayer?.start()
                _isPlaying.value = true
                _playbackState.value = _playbackState.value.copy(status = PlaybackStatus.PLAYING)
                startPositionTicker()
            }
        }
    }

    fun pause() {
        engineScope.launch {
            commandMutex.withLock {
                activeVideoView?.pause() ?: activeMediaPlayer?.pause()
                _isPlaying.value = false
                stopPositionTicker()
                _playbackState.value = _playbackState.value.copy(status = PlaybackStatus.PAUSED)
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun stop() {
        engineScope.launch {
            commandMutex.withLock {
                try {
                    activeVideoView?.stopPlayback()
                    activeMediaPlayer?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping playback", e)
                }
                _isPlaying.value = false
                _positionMs.value = 0L
                _playbackState.value = VideoPlaybackState(PlaybackStatus.IDLE)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        engineScope.launch {
            commandMutex.withLock {
                val targetPos = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
                activeVideoView?.seekTo(targetPos.toInt()) ?: activeMediaPlayer?.seekTo(targetPos.toInt())
                _positionMs.value = targetPos
                _playbackState.value = _playbackState.value.copy(positionMs = targetPos)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        activeMediaPlayer?.let { mp ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    if (mp.isPlaying) {
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed setting speed: $speed", e)
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        activeMediaPlayer?.let { mp ->
            val vol = if (muted) 0f else 1f
            mp.setVolume(vol, vol)
        }
    }

    private fun applySpeedAndVolume(mp: MediaPlayer) {
        if (_isMuted.value) {
            mp.setVolume(0f, 0f)
        }
        if (_playbackSpeed.value != 1.0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mp.playbackParams = mp.playbackParams.setSpeed(_playbackSpeed.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed applying speed", e)
            }
        }
    }

    fun updatePosition(positionMs: Long) {
        _positionMs.value = positionMs
        _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
    }

    fun release() {
        engineScope.launch {
            commandMutex.withLock {
                try {
                    activeVideoView?.stopPlayback()
                    activeMediaPlayer?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing video engine", e)
                }
                activeVideoView = null
                activeMediaPlayer = null
                _isPlaying.value = false
                _playbackState.value = VideoPlaybackState(PlaybackStatus.RELEASED)
            }
        }
    }

    companion object {
        private const val TAG = "LocalVideoEngine"

        @Volatile
        private var instance: LocalVideoEngine? = null

        fun getInstance(context: Context): LocalVideoEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalVideoEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
