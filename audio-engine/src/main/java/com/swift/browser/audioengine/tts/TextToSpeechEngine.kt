package com.swift.browser.audioengine.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class TextToSpeechEngine(private val context: Context) : TtsEngineApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _ttsState = MutableStateFlow(TtsState())
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private var segments: List<String> = emptyList()
    private var currentSegmentIndex = 0
    private var pendingSingleUtteranceCallback: (() -> Unit)? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                try {
                    tts?.language = Locale.getDefault()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                setupProgressListener()
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (utteranceId == "swift_single_utterance") {
                        val callback = pendingSingleUtteranceCallback
                        pendingSingleUtteranceCallback = null
                        callback?.invoke()
                    } else if (utteranceId?.startsWith("segment_") == true) {
                        playNext()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                scope.launch {
                    if (utteranceId == "swift_single_utterance") {
                        val callback = pendingSingleUtteranceCallback
                        pendingSingleUtteranceCallback = null
                        callback?.invoke()
                    }
                }
            }
        })
    }

    override fun startSpeakingText(text: String, onStarted: () -> Unit, onError: (String) -> Unit) {
        val trimmedText = text.trim()
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        if (trimmedText.isBlank() || trimmedText == "null") {
            onError("No text content found.")
            return
        }

        val sentenceList = trimmedText.split(Regex("(?<=[.!?\n])\\s+"))
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() && it.length > 3 }

        if (sentenceList.isEmpty()) {
            onError("No readable sentences found.")
            return
        }

        segments = sentenceList
        currentSegmentIndex = 0

        _ttsState.update {
            it.copy(
                isActive = true,
                isPlaying = true,
                currentText = sentenceList[0],
                currentIndex = 0,
                totalSegments = sentenceList.size
            )
        }

        onStarted()
        playSegment(0)
    }

    override fun speakSingleUtterance(text: String, onComplete: (() -> Unit)?) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }
        pendingSingleUtteranceCallback = onComplete
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "swift_single_utterance")
        }
        tts?.setSpeechRate(_ttsState.value.speed)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "swift_single_utterance")
    }

    override fun playSegment(index: Int) {
        if (index < 0 || index >= segments.size) {
            stop()
            return
        }
        currentSegmentIndex = index
        val textToSpeak = segments[index]

        _ttsState.update {
            it.copy(
                currentText = textToSpeak,
                currentIndex = index,
                isActive = true,
                isPlaying = true
            )
        }

        tts?.setSpeechRate(_ttsState.value.speed)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "segment_$index")
        }
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "segment_$index")
    }

    override fun playNext() {
        if (currentSegmentIndex + 1 < segments.size) {
            playSegment(currentSegmentIndex + 1)
        } else {
            stop()
        }
    }

    override fun playPrevious() {
        if (currentSegmentIndex - 1 >= 0) {
            playSegment(currentSegmentIndex - 1)
        } else {
            playSegment(currentSegmentIndex)
        }
    }

    override fun togglePlayPause() {
        if (_ttsState.value.isPlaying) {
            tts?.stop()
            _ttsState.update { it.copy(isPlaying = false) }
        } else {
            if (segments.isNotEmpty() && currentSegmentIndex in segments.indices) {
                playSegment(currentSegmentIndex)
            }
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _ttsState.update {
            it.copy(
                isActive = false,
                isPlaying = false,
                currentText = "",
                currentIndex = 0,
                totalSegments = 0
            )
        }
    }

    override fun setSpeechRate(rate: Float) {
        _ttsState.update { it.copy(speed = rate) }
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (_ttsState.value.isPlaying && segments.isNotEmpty() && currentSegmentIndex in segments.indices) {
            playSegment(currentSegmentIndex)
        }
    }

    override fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tts = null
        isInitialized = false
    }
}
