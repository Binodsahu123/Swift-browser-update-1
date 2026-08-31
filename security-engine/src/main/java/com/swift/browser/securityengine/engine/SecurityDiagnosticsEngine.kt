package com.swift.browser.securityengine.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

import java.net.URI

class SecurityDiagnosticsEngine {
    private val _diagnosticsFlow = MutableStateFlow("Security Diagnostics initialized\n")
    val diagnosticsFlow: StateFlow<String> = _diagnosticsFlow.asStateFlow()

    private val _ephemeralPrivateDiagnostics = MutableStateFlow("Ephemeral Private Diagnostics initialized\n")
    val ephemeralPrivateDiagnostics: StateFlow<String> = _ephemeralPrivateDiagnostics.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun logEvent(event: String, isPrivate: Boolean = false) {
        val timestamp = dateFormat.format(Date())
        if (isPrivate) {
            val sanitizedEvent = sanitizePrivateLog(event)
            val entry = "[$timestamp] [PRIVATE] $sanitizedEvent\n"
            val current = _diagnosticsFlow.value
            _diagnosticsFlow.value = if (current.length > 5000) entry else current + entry

            val rawEntry = "[$timestamp] [PRIVATE_EPHEMERAL] $event\n"
            val currentEph = _ephemeralPrivateDiagnostics.value
            _ephemeralPrivateDiagnostics.value = if (currentEph.length > 5000) rawEntry else currentEph + rawEntry
        } else {
            val entry = "[$timestamp] $event\n"
            val current = _diagnosticsFlow.value
            _diagnosticsFlow.value = if (current.length > 5000) entry else current + entry
        }
    }

    private fun sanitizePrivateLog(event: String): String {
        val urlRegex = Regex("https?://[^\\s]+")
        return urlRegex.replace(event) { match ->
            try {
                val uri = URI(match.value)
                val host = uri.host ?: match.value
                "${uri.scheme}://$host/[REDACTED_PRIVATE_PATH]"
            } catch (e: Exception) {
                "[REDACTED_PRIVATE_URL]"
            }
        }
    }

    fun getDiagnosticsLog(): String {
        return _diagnosticsFlow.value
    }

    fun getEphemeralPrivateLog(): String {
        return _ephemeralPrivateDiagnostics.value
    }

    fun clearEphemeralPrivateLog() {
        _ephemeralPrivateDiagnostics.value = "Ephemeral Private Diagnostics cleared\n"
    }
}
