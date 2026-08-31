package com.swift.browser.videoengine.core

import android.app.Activity
import android.content.Context
import com.swift.browser.videoengine.controls.GestureController
import com.swift.browser.videoengine.controls.PlaybackSpeedManager
import com.swift.browser.videoengine.controls.VideoAspectRatioController
import com.swift.browser.videoengine.controls.VideoBrightnessController
import com.swift.browser.videoengine.controls.VideoVolumeController
import com.swift.browser.videoengine.extraction.VideoAudioExtractionManager
import com.swift.browser.videoengine.fullscreen.FullscreenVideoManager
import com.swift.browser.videoengine.history.WatchHistoryManager
import com.swift.browser.videoengine.library.VideoLibraryManager
import com.swift.browser.videoengine.model.VideoItem
import com.swift.browser.videoengine.pip.PictureInPictureManager
import com.swift.browser.videoengine.playback.VideoQueueManager
import com.swift.browser.videoengine.session.MediaSessionManager
import com.swift.browser.videoengine.playback.LocalVideoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoPlayerEngine private constructor(private val context: Context) {

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val localEngine = LocalVideoEngine.getInstance(context)
    val libraryManager = VideoLibraryManager(context)
    val queueManager = VideoQueueManager()
    val speedManager = PlaybackSpeedManager()
    val volumeController = VideoVolumeController(context)
    val brightnessController = VideoBrightnessController()
    val aspectRatioController = VideoAspectRatioController()
    val gestureController = GestureController()
    val pipManager = PictureInPictureManager(context)
    val mediaSessionManager = MediaSessionManager(context)
    val audioExtractor = VideoAudioExtractionManager(context)

    val playbackState: StateFlow<VideoPlaybackState> = localEngine.playbackState
    val currentVideo: StateFlow<VideoItem?> = localEngine.currentVideo
    val isPlaying: StateFlow<Boolean> = localEngine.isPlaying
    val positionMs: StateFlow<Long> = localEngine.positionMs
    val durationMs: StateFlow<Long> = localEngine.durationMs

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private var progressJob: Job? = null

    init {
        mediaSessionManager.initialize()
        mediaSessionManager.onPlayHandler = { resume() }
        mediaSessionManager.onPauseHandler = { pause() }
        mediaSessionManager.onNextHandler = { next() }
        mediaSessionManager.onPreviousHandler = { previous() }
        mediaSessionManager.onSeekHandler = { pos -> seekTo(pos) }

        localEngine.onCompletionListener = {
            engineScope.launch {
                next()
            }
        }

        libraryManager.scanVideos(engineScope)
    }

    fun play(video: VideoItem) {
        queueManager.playVideo(video)
        localEngine.loadAndPlay(video, autoPlay = true)
        libraryManager.historyManager.addToHistory(video)
        mediaSessionManager.updateMetadata(video)
        mediaSessionManager.updateState(true, 0L)
    }

    fun pause() {
        localEngine.pause()
        mediaSessionManager.updateState(false, positionMs.value)
    }

    fun resume() {
        if (currentVideo.value != null) {
            localEngine.play()
            mediaSessionManager.updateState(true, positionMs.value)
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) pause() else resume()
    }

    fun stop() {
        localEngine.stop()
        mediaSessionManager.updateState(false, 0L)
        progressJob?.cancel()
    }

    fun seekTo(positionMs: Long) {
        localEngine.seekTo(positionMs)
        mediaSessionManager.updateState(isPlaying.value, positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        speedManager.setSpeed(speed)
        localEngine.setPlaybackSpeed(speed)
    }

    fun setMuted(muted: Boolean) {
        localEngine.setMuted(muted)
    }

    fun updatePlaybackProgress(position: Long, duration: Long) {
        localEngine.updatePosition(position)
    }

    fun next() {
        val nextVideo = queueManager.next()
        if (nextVideo != null) {
            play(nextVideo)
        } else {
            stop()
        }
    }

    fun previous() {
        val prevVideo = queueManager.previous()
        if (prevVideo != null) {
            play(prevVideo)
        }
    }

    fun setQueue(videos: List<VideoItem>, initialIndex: Int = 0) {
        queueManager.setQueue(videos, initialIndex)
        val selected = queueManager.currentVideo
        if (selected != null) {
            play(selected)
        }
    }

    fun setFullscreen(fullscreen: Boolean, activity: Activity? = null) {
        _isFullscreen.value = fullscreen
        activity?.let {
            val manager = FullscreenVideoManager(it)
            if (fullscreen) manager.enterFullscreen() else manager.exitFullscreen()
        }
    }

    fun enterPictureInPicture(activity: Activity?): Boolean {
        return pipManager.enterPictureInPicture(activity, isPlaying.value)
    }

    suspend fun extractAudio(video: VideoItem): String? {
        return audioExtractor.extractAudio(video)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = engineScope.launch {
            while (isPlaying.value) {
                delay(500)
                if (durationMs.value > 0 && positionMs.value >= durationMs.value) {
                    next()
                    break
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: VideoPlayerEngine? = null

        fun getInstance(context: Context): VideoPlayerEngine {
            return instance ?: synchronized(this) {
                instance ?: VideoPlayerEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
