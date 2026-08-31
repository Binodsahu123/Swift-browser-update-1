package com.swift.browser.searchengine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionManager(
    private val context: Context,
    private val onPartial: ((String) -> Unit)? = null,
    private val onResult: ((String) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null,
    private val onReadyForSpeech: (() -> Unit)? = null,
    private val onBeginningOfSpeech: (() -> Unit)? = null,
    private val onEndOfSpeech: (() -> Unit)? = null,
    private val onErrorDetailed: ((errorCode: String, errorMsg: String) -> Unit)? = null
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(languageCode: String = "en-US") {
        stopListening()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("SpeechRecognitionMgr", "SpeechRecognizer is not available on this device")
            onErrorDetailed?.invoke("service-not-allowed", "Speech recognition service is not available on this device")
            onError?.invoke("Speech recognition not available")
            return
        }
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onReadyForSpeech?.invoke()
                }

                override fun onBeginningOfSpeech() {
                    onBeginningOfSpeech?.invoke()
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    onEndOfSpeech?.invoke()
                }

                override fun onError(error: Int) {
                    val (code, msg) = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "audio-capture" to "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "audio-capture" to "Speech recognition client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed" to "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "network" to "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network" to "Network operation timed out"
                        SpeechRecognizer.ERROR_NO_MATCH -> "no-speech" to "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "audio-capture" to "Audio recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "network" to "Speech recognition server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech" to "No speech heard within timeout"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "service-not-allowed" to "Language model not supported"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "service-not-allowed" to "Language model unavailable"
                        else -> "audio-capture" to "Speech recognition failed"
                    }
                    onErrorDetailed?.invoke(code, msg)
                    onError?.invoke(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult?.invoke(matches[0])
                    } else {
                        onErrorDetailed?.invoke("no-speech", "No speech detected")
                        onError?.invoke("No words heard")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onPartial?.invoke(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            Log.i("SpeechRecognitionMgr", "SpeechRecognizer started listening with lang code: $languageCode")
        } catch (e: Exception) {
            Log.e("SpeechRecognitionMgr", "Failed to start speech recognition", e)
            onErrorDetailed?.invoke("audio-capture", "Speech recognition initialization failed")
            onError?.invoke(e.message ?: "Launch failed")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechRecognitionMgr", "Error stopping recognizer", e)
        }
        speechRecognizer = null
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechRecognitionMgr", "Error cancelling recognizer", e)
        }
        speechRecognizer = null
    }
}
