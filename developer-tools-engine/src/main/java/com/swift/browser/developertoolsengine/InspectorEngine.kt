package com.swift.browser.developertoolsengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel {
    INFO, WARNING, ERROR, DEBUG, LOG
}

data class ConsoleLog(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkRequest(
    val id: String,
    val url: String,
    val method: String,
    val statusCode: Int,
    val startTime: Long,
    val durationMs: Long,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val requestBody: String,
    val responseBody: String
)

data class StorageEntry(
    val key: String,
    val value: String,
    val type: String // LocalStorage, SessionStorage, Cookie, IndexedDB
)

class InspectorEngine {
    val consoleLogs: StateFlow<List<ConsoleLog>> = ConsoleEngine.instance.consoleLogs

    private val _networkRequests = MutableStateFlow<List<NetworkRequest>>(emptyList())
    val networkRequests: StateFlow<List<NetworkRequest>> = _networkRequests.asStateFlow()

    private val _storageEntries = MutableStateFlow<List<StorageEntry>>(emptyList())
    val storageEntries: StateFlow<List<StorageEntry>> = _storageEntries.asStateFlow()

    private val _inspectModeEnabled = MutableStateFlow(false)
    val inspectModeEnabled: StateFlow<Boolean> = _inspectModeEnabled.asStateFlow()

    private val _highlightedElementHtml = MutableStateFlow("")
    val highlightedElementHtml: StateFlow<String> = _highlightedElementHtml.asStateFlow()

    private val _fullDOM = MutableStateFlow("")
    val fullDOM: StateFlow<String> = _fullDOM.asStateFlow()

    fun updateFullDOM(html: String) {
        _fullDOM.value = html
    }

    fun logConsole(level: LogLevel, message: String) {
        if (!isEnabled) return
        ConsoleEngine.instance.addLog(level, message)
    }

    fun clearConsole() {
        ConsoleEngine.instance.clearAllLogs()
    }

    fun recordNetworkRequest(request: NetworkRequest) {
        if (!isEnabled) return
        _networkRequests.update { current ->
            val updated = current + request
            if (updated.size > 150) {
                updated.drop(updated.size - 150)
            } else {
                updated
            }
        }
    }

    fun clearNetwork() {
        _networkRequests.value = emptyList()
    }

    fun setStorageEntries(entries: List<StorageEntry>) {
        _storageEntries.value = entries
    }

    fun setInspectModeEnabled(enabled: Boolean) {
        _inspectModeEnabled.value = enabled
    }

    fun updateHighlightedElement(html: String) {
        _highlightedElementHtml.value = html
    }

    companion object {
        @Volatile var isEnabled: Boolean = false
        val instance = InspectorEngine()
    }
}
