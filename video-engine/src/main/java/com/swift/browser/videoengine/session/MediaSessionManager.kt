package com.swift.browser.videoengine.session

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import com.swift.browser.videoengine.model.VideoItem

class MediaSessionManager(private val context: Context) {
    private var mediaSession: MediaSession? = null

    var onPlayHandler: (() -> Unit)? = null
    var onPauseHandler: (() -> Unit)? = null
    var onNextHandler: (() -> Unit)? = null
    var onPreviousHandler: (() -> Unit)? = null
    var onSeekHandler: ((Long) -> Unit)? = null

    fun initialize() {
        if (mediaSession != null) return
        try {
            mediaSession = MediaSession(context, "SwiftBrowserVideoMediaSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                val state = PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                                PlaybackState.ACTION_PAUSE or
                                PlaybackState.ACTION_SKIP_TO_NEXT or
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackState.ACTION_SEEK_TO
                    )
                    .setState(PlaybackState.STATE_STOPPED, 0, 1.0f)
                    .build()
                setPlaybackState(state)

                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        Log.i(TAG, "MediaSession: onPlay")
                        onPlayHandler?.invoke()
                    }

                    override fun onPause() {
                        Log.i(TAG, "MediaSession: onPause")
                        onPauseHandler?.invoke()
                    }

                    override fun onSkipToNext() {
                        Log.i(TAG, "MediaSession: onSkipToNext")
                        onNextHandler?.invoke()
                    }

                    override fun onSkipToPrevious() {
                        Log.i(TAG, "MediaSession: onSkipToPrevious")
                        onPreviousHandler?.invoke()
                    }

                    override fun onSeekTo(pos: Long) {
                        Log.i(TAG, "MediaSession: onSeekTo $pos")
                        onSeekHandler?.invoke(pos)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaSession", e)
        }
    }

    fun updateMetadata(video: VideoItem?) {
        if (video == null || mediaSession == null) return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, video.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, video.folder)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, video.duration ?: 0L)
            .build()
        mediaSession?.setMetadata(metadata)
    }

    fun updateState(isPlaying: Boolean, positionMs: Long) {
        mediaSession?.let { session ->
            val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SEEK_TO
                )
                .setState(stateCode, positionMs, 1.0f)
                .build()
            session.setPlaybackState(state)
        }
    }

    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }

    companion object {
        private const val TAG = "MediaSessionManager"
    }
}
