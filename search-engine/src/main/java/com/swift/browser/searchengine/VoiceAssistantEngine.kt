package com.swift.browser.searchengine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface VoiceEngineApi {
    val isListening: StateFlow<Boolean>
    val transcript: StateFlow<String>
    val rmsDb: StateFlow<Float>
    val activeMode: StateFlow<String>
    val activeLanguageCode: StateFlow<String>
    val isWakeWordEnabled: StateFlow<Boolean>
    val chatSessions: StateFlow<List<VoiceChatMessage>>
    val voiceNotes: StateFlow<List<VoiceNote>>
    val voiceHistory: StateFlow<List<VoiceHistoryEntry>>
    val errorMessage: StateFlow<String?>

    fun startListening(languageCode: String? = null, onResult: (String) -> Unit = {})
    fun stopListening()
    fun setActiveMode(mode: String)
    fun setActiveLanguage(lang: String, code: String)
    fun toggleWakeWord()
    fun addChatMessage(role: String, text: String)
    fun clearChat()
    fun saveVoiceNote(note: VoiceNote)
    fun deleteVoiceNote(id: String)
    fun addHistoryEntry(text: String, type: String)
    fun clearHistory()
    fun setErrorMessage(msg: String?)
    fun destroy()

    companion object {
        fun create(context: Context): VoiceEngineApi {
            return VoiceAssistantEngine(context)
        }
    }
}

class VoiceAssistantEngine(private val context: Context) : VoiceEngineApi {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcript = MutableStateFlow("")
    override val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    override val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _activeMode = MutableStateFlow("Assistant")
    override val activeMode: StateFlow<String> = _activeMode.asStateFlow()

    private val _activeLanguageCode = MutableStateFlow("en-US")
    override val activeLanguageCode: StateFlow<String> = _activeLanguageCode.asStateFlow()

    private val _isWakeWordEnabled = MutableStateFlow(true)
    override val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<VoiceChatMessage>>(emptyList())
    override val chatSessions: StateFlow<List<VoiceChatMessage>> = _chatSessions.asStateFlow()

    private val _voiceNotes = MutableStateFlow<List<VoiceNote>>(emptyList())
    override val voiceNotes: StateFlow<List<VoiceNote>> = _voiceNotes.asStateFlow()

    private val _voiceHistory = MutableStateFlow<List<VoiceHistoryEntry>>(emptyList())
    override val voiceHistory: StateFlow<List<VoiceHistoryEntry>> = _voiceHistory.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var speechRecognitionManager: SpeechRecognitionManager? = null

    override fun startListening(languageCode: String?, onResult: (String) -> Unit) {
        val targetLang = languageCode ?: _activeLanguageCode.value
        _isListening.value = true
        _errorMessage.value = null
        _transcript.value = "Listening..."

        speechRecognitionManager?.stopListening()
        speechRecognitionManager = SpeechRecognitionManager(
            context = context,
            onPartial = { partial ->
                _transcript.value = partial
            },
            onResult = { result ->
                _transcript.value = result
                _isListening.value = false
                addHistoryEntry(result, _activeMode.value)
                onResult(result)
            },
            onError = { error ->
                _isListening.value = false
                _errorMessage.value = error
            }
        )
        speechRecognitionManager?.startListening(targetLang)
    }

    override fun stopListening() {
        speechRecognitionManager?.stopListening()
        speechRecognitionManager = null
        _isListening.value = false
    }

    override fun setActiveMode(mode: String) {
        _activeMode.value = mode
    }

    override fun setActiveLanguage(lang: String, code: String) {
        _activeLanguageCode.value = code
    }

    override fun toggleWakeWord() {
        _isWakeWordEnabled.update { !it }
    }

    override fun addChatMessage(role: String, text: String) {
        val msg = VoiceChatMessage(role = role, text = text)
        _chatSessions.update { it + msg }
    }

    override fun clearChat() {
        _chatSessions.value = emptyList()
    }

    override fun saveVoiceNote(note: VoiceNote) {
        _voiceNotes.update { it + note }
    }

    override fun deleteVoiceNote(id: String) {
        _voiceNotes.update { list -> list.filterNot { it.id == id } }
    }

    override fun addHistoryEntry(text: String, type: String) {
        val entry = VoiceHistoryEntry(text = text, type = type)
        _voiceHistory.update { it + entry }
    }

    override fun clearHistory() {
        _voiceHistory.value = emptyList()
    }

    override fun setErrorMessage(msg: String?) {
        _errorMessage.value = msg
    }

    override fun destroy() {
        stopListening()
    }
}
