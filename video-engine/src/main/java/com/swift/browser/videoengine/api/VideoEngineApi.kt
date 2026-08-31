package com.swift.browser.videoengine.api

import android.app.Activity
import android.content.Context
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.model.VideoItem
import com.swift.browser.videoengine.model.VideoPlaylist
import kotlinx.coroutines.flow.StateFlow

object VideoEngineApi {

    fun getEngine(context: Context): VideoPlayerEngine {
        return VideoPlayerEngine.getInstance(context)
    }

    fun play(context: Context, video: VideoItem) {
        getEngine(context).play(video)
    }

    fun pause(context: Context) {
        getEngine(context).pause()
    }

    fun resume(context: Context) {
        getEngine(context).resume()
    }

    fun togglePlayPause(context: Context) {
        getEngine(context).togglePlayPause()
    }

    fun stop(context: Context) {
        getEngine(context).stop()
    }

    fun seekTo(context: Context, positionMs: Long) {
        getEngine(context).seekTo(positionMs)
    }

    fun next(context: Context) {
        getEngine(context).next()
    }

    fun previous(context: Context) {
        getEngine(context).previous()
    }

    fun setQueue(context: Context, videos: List<VideoItem>, initialIndex: Int = 0) {
        getEngine(context).setQueue(videos, initialIndex)
    }

    fun setPlaybackSpeed(context: Context, speed: Float) {
        getEngine(context).setPlaybackSpeed(speed)
    }

    fun setVolume(context: Context, volume: Int) {
        getEngine(context).volumeController.setVolume(volume)
    }

    fun toggleMute(context: Context) {
        getEngine(context).volumeController.toggleMute()
    }

    fun setBrightness(context: Context, activity: Activity?, level: Float) {
        getEngine(context).brightnessController.setBrightness(activity, level)
    }

    fun getBrightness(context: Context, activity: Activity?): Float {
        return getEngine(context).brightnessController.getScreenBrightness(activity)
    }

    fun setAspectRatio(context: Context, mode: String) {
        getEngine(context).aspectRatioController.setMode(mode)
    }

    fun toggleLock(context: Context) {
        getEngine(context).gestureController.toggleLock()
    }

    fun toggleMirror(context: Context) {
        getEngine(context).gestureController.toggleMirror()
    }

    fun enterFullscreen(context: Context, activity: Activity? = null) {
        getEngine(context).setFullscreen(true, activity)
    }

    fun exitFullscreen(context: Context, activity: Activity? = null) {
        getEngine(context).setFullscreen(false, activity)
    }

    fun enterPictureInPicture(context: Context, activity: Activity?): Boolean {
        return getEngine(context).enterPictureInPicture(activity)
    }

    private var activeFullscreenController: com.swift.browser.videoengine.fullscreen.FullscreenVideoController? = null
    private var activeFullscreenManager: com.swift.browser.videoengine.fullscreen.FullscreenVideoManager? = null
    private var activeFullscreenContainer: android.view.ViewGroup? = null

    fun showCustomView(activity: Activity, view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
        val manager = com.swift.browser.videoengine.fullscreen.FullscreenVideoManager(activity)
        val decorView = activity.window.decorView as android.view.ViewGroup
        val fullscreenContainer = android.widget.FrameLayout(activity).apply {
            id = android.view.View.generateViewId()
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        decorView.addView(fullscreenContainer, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        
        val controller = com.swift.browser.videoengine.fullscreen.FullscreenVideoController(
            mainContainer = decorView.getChildAt(0) as android.view.ViewGroup, // Just a placeholder to hide, though hiding decorView's child might hide Compose.
            fullscreenContainer = fullscreenContainer
        )
        controller.onShowCustomView(view, callback)
        manager.enterFullscreen()
        
        activeFullscreenController = controller
        activeFullscreenManager = manager
        activeFullscreenContainer = fullscreenContainer
    }

    fun hideCustomView(activity: Activity) {
        activeFullscreenController?.onHideCustomView()
        activeFullscreenManager?.exitFullscreen()
        activeFullscreenContainer?.let {
            (activity.window.decorView as android.view.ViewGroup).removeView(it)
        }
        activeFullscreenController = null
        activeFullscreenManager = null
        activeFullscreenContainer = null
    }

    fun isCustomViewShowing(): Boolean {
        return activeFullscreenController?.isFullscreen() == true
    }

    suspend fun extractAudio(context: Context, video: VideoItem): String? {
        return getEngine(context).extractAudio(video)
    }

    // --- Library, Scanning & Playlist Management Delegates ---
    fun getAllVideos(context: Context): StateFlow<List<VideoItem>> {
        return getEngine(context).libraryManager.allVideos
    }

    fun getPlaylists(context: Context): StateFlow<List<VideoPlaylist>> {
        return getEngine(context).libraryManager.playlistManager.playlists
    }

    fun scanVideos(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
        getEngine(context).libraryManager.scanVideos(scope)
    }

    fun deleteVideo(context: Context, scope: kotlinx.coroutines.CoroutineScope, video: VideoItem) {
        getEngine(context).libraryManager.deleteVideo(scope, video)
    }

    fun renameVideo(context: Context, scope: kotlinx.coroutines.CoroutineScope, video: VideoItem, newName: String) {
        getEngine(context).libraryManager.renameVideo(scope, video, newName)
    }

    fun createPlaylist(context: Context, name: String): VideoPlaylist {
        return getEngine(context).libraryManager.playlistManager.createPlaylist(name)
    }

    fun renamePlaylist(context: Context, id: String, newName: String) {
        getEngine(context).libraryManager.playlistManager.renamePlaylist(id, newName)
    }

    fun deletePlaylist(context: Context, id: String) {
        getEngine(context).libraryManager.playlistManager.deletePlaylist(id)
    }

    fun addToPlaylist(context: Context, playlistId: String, video: VideoItem) {
        getEngine(context).libraryManager.playlistManager.addToPlaylist(playlistId, video)
    }

    fun removeFromPlaylist(context: Context, playlistId: String, videoId: String) {
        getEngine(context).libraryManager.playlistManager.removeFromPlaylist(playlistId, videoId)
    }
}
