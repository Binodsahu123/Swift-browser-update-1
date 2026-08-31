package com.swift.browser.audioengine.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class AudioSessionEngine(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    fun requestAudioFocus(onLoss: () -> Unit, onDuck: () -> Unit, onGain: () -> Unit): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> onLoss()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLoss()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck()
                        AudioManager.AUDIOFOCUS_GAIN -> onGain()
                    }
                }
            }.build()
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> onLoss()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLoss()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck()
                        AudioManager.AUDIOFOCUS_GAIN -> onGain()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
