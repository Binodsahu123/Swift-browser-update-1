package com.swift.browser.videoengine.controls

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackSpeedManager {
    val supportedSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    fun setSpeed(speed: Float) {
        if (speed in supportedSpeeds || speed in 0.25f..3.0f) {
            _currentSpeed.value = speed
        }
    }
}

class VideoVolumeController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val maxVolume: Int = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

    private val _volume = MutableStateFlow(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxVolume / 2)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var previousVolume: Int = _volume.value

    fun setVolume(newVolume: Int) {
        val clamped = newVolume.coerceIn(0, maxVolume)
        _volume.value = clamped
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        } catch (e: Exception) {
            Log.e("VideoVolumeController", "Error setting volume", e)
        }
        if (clamped > 0) {
            _isMuted.value = false
        }
    }

    fun toggleMute() {
        if (_isMuted.value) {
            _isMuted.value = false
            setVolume(if (previousVolume > 0) previousVolume else maxVolume / 2)
        } else {
            previousVolume = _volume.value
            _isMuted.value = true
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } catch (e: Exception) {
                Log.e("VideoVolumeController", "Error muting", e)
            }
        }
    }
}

class VideoBrightnessController {
    private val _brightness = MutableStateFlow(0.5f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    fun getScreenBrightness(activity: Activity?): Float {
        if (activity == null) return 0.5f
        val lp = activity.window.attributes
        return if (lp.screenBrightness >= 0) lp.screenBrightness else {
            try {
                val sysBrightness = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                sysBrightness / 255f
            } catch (e: Exception) {
                0.5f
            }
        }
    }

    fun setBrightness(activity: Activity?, level: Float) {
        val clamped = level.coerceIn(0.01f, 1.0f)
        _brightness.value = clamped
        activity?.let {
            val lp = it.window.attributes
            lp.screenBrightness = clamped
            it.window.attributes = lp
        }
    }
}

class VideoAspectRatioController {
    val modes = listOf("Fit to screen", "Crop", "Stretch")

    private val _mode = MutableStateFlow("Fit to screen")
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun setMode(newMode: String) {
        if (newMode in modes) {
            _mode.value = newMode
        }
    }

    fun cycleMode(): String {
        val nextIdx = (modes.indexOf(_mode.value) + 1) % modes.size
        _mode.value = modes[nextIdx]
        return _mode.value
    }
}

class GestureController {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isMirrored = MutableStateFlow(false)
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
    }

    fun setLock(locked: Boolean) {
        _isLocked.value = locked
    }

    fun toggleMirror() {
        _isMirrored.value = !_isMirrored.value
    }

    fun setMirror(mirrored: Boolean) {
        _isMirrored.value = mirrored
    }
}
