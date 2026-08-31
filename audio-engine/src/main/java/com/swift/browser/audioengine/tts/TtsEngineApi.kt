package com.swift.browser.audioengine.tts

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface TtsEngineApi {
    val ttsState: StateFlow<TtsState>

    fun startSpeakingText(text: String, onStarted: () -> Unit = {}, onError: (String) -> Unit = {})
    fun speakSingleUtterance(text: String, onComplete: (() -> Unit)? = null)
    fun playSegment(index: Int)
    fun playNext()
    fun playPrevious()
    fun togglePlayPause()
    fun stop()
    fun setSpeechRate(rate: Float)
    fun release()

    companion object {
        fun create(context: Context): TtsEngineApi {
            return TextToSpeechEngine(context)
        }
    }
}
