package com.swift.browser.audioengine.engine

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.swift.browser.audioengine.model.AudioTrackItem
import java.io.File

class LocalAudioEngine(
    private val context: Context,
    private val sessionEngine: AudioSessionEngine
) {
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    enum class PlayState {
        IDLE, PREPARING, READY, PLAYING, PAUSED, COMPLETED, RELEASED, ERROR
    }

    private var currentState = PlayState.IDLE

    var onCompletionListener: (() -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    @Synchronized
    fun playTrack(track: AudioTrackItem, onStarted: () -> Unit) {
        currentState = PlayState.PREPARING
        
        // Construct preferred Content URI first
        var uri: Uri? = null
        try {
            val trackIdLong = track.id.toLongOrNull()
            if (trackIdLong != null && trackIdLong > 0) {
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackIdLong)
            }
        } catch (_: Exception) {}

        if (uri == null) {
            val filePath = track.filePath
            uri = if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
                Uri.parse(filePath)
            } else if (filePath.isNotEmpty()) {
                Uri.fromFile(File(filePath))
            } else {
                null
            }
        }

        if (uri == null) {
            Log.e("LocalAudioEngine", "No valid path or content URI for track: ${track.title}")
            currentState = PlayState.ERROR
            onErrorListener?.invoke("Track path is missing or invalid")
            return
        }

        // Verify URI accessibility
        var isAccessible = false
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                isAccessible = true
            }
        } catch (_: Exception) {}

        // Fallback to direct file if content URI wasn't directly accessible
        if (!isAccessible && track.filePath.isNotEmpty() && !track.filePath.startsWith("content://")) {
            val file = File(track.filePath)
            if (file.exists() && file.canRead()) {
                uri = Uri.fromFile(file)
                isAccessible = true
            }
        }

        if (!isAccessible && !uri.toString().startsWith("http")) {
            Log.e("LocalAudioEngine", "URI or File is not readable: $uri")
            currentState = PlayState.ERROR
            onErrorListener?.invoke("Audio file is not accessible or has been deleted")
            return
        }

        try {
            releaseInternal()

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
            }

            mediaPlayer = player

            player.setOnPreparedListener { mp ->
                synchronized(this@LocalAudioEngine) {
                    if (currentState != PlayState.PREPARING) {
                        mp.release()
                        return@setOnPreparedListener
                    }
                    currentState = PlayState.READY
                    
                    val focusGranted = sessionEngine.requestAudioFocus(
                        onLoss = { pause() },
                        onDuck = { mediaPlayer?.setVolume(0.2f, 0.2f) },
                        onGain = { mediaPlayer?.setVolume(1.0f, 1.0f); resume() }
                    )

                    if (focusGranted) {
                        try {
                            mp.start()
                            currentState = PlayState.PLAYING
                            onStarted()
                        } catch (e: Exception) {
                            Log.e("LocalAudioEngine", "Error starting media player", e)
                            currentState = PlayState.ERROR
                            onErrorListener?.invoke("Failed to start media player")
                        }
                    } else {
                        currentState = PlayState.PAUSED
                    }
                }
            }

            player.setOnCompletionListener {
                synchronized(this@LocalAudioEngine) {
                    currentState = PlayState.COMPLETED
                    onCompletionListener?.invoke()
                }
            }

            player.setOnErrorListener { _, what, extra ->
                synchronized(this@LocalAudioEngine) {
                    Log.e("LocalAudioEngine", "MediaPlayer Error occurred: what=$what, extra=$extra")
                    currentState = PlayState.ERROR
                    onErrorListener?.invoke("Playback error ($what, $extra)")
                    releaseInternal()
                    true
                }
            }

            player.prepareAsync()
            applyAudioEffects()

        } catch (e: Exception) {
            Log.e("LocalAudioEngine", "Error in setup of media player for track: ${track.title}", e)
            currentState = PlayState.ERROR
            onErrorListener?.invoke(e.message ?: "Failed to initialize audio player")
        }
    }

    private fun applyAudioEffects() {
        mediaPlayer?.let { player ->
            try {
                equalizer = Equalizer(0, player.audioSessionId).apply {
                    enabled = true
                }
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId).apply {
                    setTargetGain(100)
                    enabled = true
                }
            } catch (e: Exception) {
                Log.e("LocalAudioEngine", "Failed to apply audio effects", e)
            }
        }
    }

    @Synchronized
    fun play() {
        mediaPlayer?.let { player ->
            if (currentState == PlayState.PAUSED || currentState == PlayState.READY) {
                val focusGranted = sessionEngine.requestAudioFocus(
                    onLoss = { pause() },
                    onDuck = { mediaPlayer?.setVolume(0.2f, 0.2f) },
                    onGain = { mediaPlayer?.setVolume(1.0f, 1.0f); resume() }
                )
                if (focusGranted) {
                    try {
                        player.start()
                        currentState = PlayState.PLAYING
                    } catch (e: Exception) {
                        Log.e("LocalAudioEngine", "Play command failed", e)
                    }
                }
            }
        }
    }

    @Synchronized
    fun pause() {
        mediaPlayer?.let { player ->
            if (currentState == PlayState.PLAYING) {
                try {
                    player.pause()
                    currentState = PlayState.PAUSED
                } catch (e: Exception) {
                    Log.e("LocalAudioEngine", "Pause command failed", e)
                }
            }
        }
    }

    @Synchronized
    fun resume() {
        mediaPlayer?.let { player ->
            if (currentState == PlayState.PAUSED || currentState == PlayState.READY) {
                try {
                    player.start()
                    currentState = PlayState.PLAYING
                } catch (e: Exception) {
                    Log.e("LocalAudioEngine", "Resume command failed", e)
                }
            }
        }
    }

    @Synchronized
    fun seekTo(positionMs: Int) {
        if (currentState == PlayState.PLAYING || currentState == PlayState.PAUSED || currentState == PlayState.READY || currentState == PlayState.COMPLETED) {
            try {
                mediaPlayer?.seekTo(positionMs)
            } catch (e: Exception) {
                Log.e("LocalAudioEngine", "Seek failed", e)
            }
        }
    }

    @Synchronized
    fun isPlaying(): Boolean {
        return currentState == PlayState.PLAYING && mediaPlayer?.isPlaying == true
    }

    @Synchronized
    fun getCurrentPosition(): Long {
        return if (currentState == PlayState.PLAYING || currentState == PlayState.PAUSED || currentState == PlayState.READY || currentState == PlayState.COMPLETED) {
            try {
                mediaPlayer?.currentPosition?.toLong() ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }
    }

    @Synchronized
    fun getDuration(): Long {
        return if (currentState == PlayState.PLAYING || currentState == PlayState.PAUSED || currentState == PlayState.READY || currentState == PlayState.COMPLETED) {
            try {
                mediaPlayer?.duration?.toLong() ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }
    }

    @Synchronized
    fun release() {
        releaseInternal()
        sessionEngine.abandonAudioFocus()
    }

    private fun releaseInternal() {
        currentState = PlayState.RELEASED
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            equalizer?.release()
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        equalizer = null
        loudnessEnhancer = null
    }
}
