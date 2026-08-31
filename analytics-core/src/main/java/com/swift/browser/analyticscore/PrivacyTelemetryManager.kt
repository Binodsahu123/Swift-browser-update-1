package com.swift.browser.analyticscore

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class PrivacyTelemetryManager {
    private val _isTelemetryEnabled = MutableStateFlow(true)
    val isTelemetryEnabled: StateFlow<Boolean> = _isTelemetryEnabled.asStateFlow()

    private val _telemetryPayloads = MutableStateFlow<List<PrivacyTelemetryPayload>>(emptyList())
    val telemetryPayloads: StateFlow<List<PrivacyTelemetryPayload>> = _telemetryPayloads.asStateFlow()

    fun setTelemetryEnabled(enabled: Boolean) {
        _isTelemetryEnabled.value = enabled
    }

    fun recordPrivacyTelemetry(
        sessionId: String,
        eventType: String,
        params: Map<String, String>
    ) {
        if (!_isTelemetryEnabled.value) return

        val hashedSession = hashAnonymously(sessionId)
        val scrubbedParams = params.mapValues { (key, value) ->
            if (key.contains("url", ignoreCase = true)) {
                sanitizeUrl(value)
            } else {
                sanitizeString(value)
            }
        }

        val payload = PrivacyTelemetryPayload(
            anonymousSessionHash = hashedSession,
            eventType = eventType,
            scrubbedParams = scrubbedParams
        )

        val current = _telemetryPayloads.value.toMutableList()
        if (current.size >= 100) {
            current.removeAt(0)
        }
        current.add(payload)
        _telemetryPayloads.value = current
    }

    companion object {
        fun sanitizeUrl(url: String): String {
            return try {
                val uri = Uri.parse(url)
                val scheme = uri.scheme ?: "http"
                val host = uri.host ?: ""
                val path = uri.path ?: ""
                if (host.isEmpty()) return "[private_url]"
                "$scheme://$host$path"
            } catch (e: Exception) {
                "[sanitized_url]"
            }
        }

        fun sanitizeString(input: String): String {
            // Scrub email addresses, tokens, phone numbers
            return input
                .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), "[REDACTED_EMAIL]")
                .replace(Regex("(?i)(key|token|auth|password)=[^&]+"), "$1=[REDACTED]")
        }

        private fun hashAnonymously(input: String): String {
            return try {
                val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
                bytes.joinToString("") { "%02x".format(it) }.take(16)
            } catch (e: Exception) {
                "anon_hash"
            }
        }
    }
}
