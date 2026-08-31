package com.swift.browser.analyticscore

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationAnalyticsManager {
    private val _navigationTraces = MutableStateFlow<List<NavigationTrace>>(emptyList())
    val navigationTraces: StateFlow<List<NavigationTrace>> = _navigationTraces.asStateFlow()

    private val _domainVisits = MutableStateFlow<Map<String, Int>>(emptyMap())
    val domainVisits: StateFlow<Map<String, Int>> = _domainVisits.asStateFlow()

    fun trackNavigationCompleted(
        url: String,
        loadDurationMs: Long,
        httpCode: Int = 200,
        isSuccess: Boolean = true,
        context: AnalyticsContext = AnalyticsContext.NORMAL
    ) {
        if (context.isPrivate) {
            // DO NOT persist full URL, query string, page title, or origin-specific domain navigation history for private browsing
            val trace = NavigationTrace(
                rawUrl = "[PRIVATE_BROWSING]",
                sanitizedUrl = "[PRIVATE_BROWSING]",
                domain = "[PRIVATE_ORIGIN]",
                loadDurationMs = loadDurationMs,
                httpCode = httpCode,
                isSuccess = isSuccess
            )

            val current = _navigationTraces.value.toMutableList()
            if (current.size >= 250) {
                current.removeAt(0)
            }
            current.add(trace)
            _navigationTraces.value = current
            // Do NOT record into _domainVisits for private visits
            return
        }

        val sanitized = PrivacyTelemetryManager.sanitizeUrl(url)
        val domain = extractDomain(url)

        val trace = NavigationTrace(
            rawUrl = url,
            sanitizedUrl = sanitized,
            domain = domain,
            loadDurationMs = loadDurationMs,
            httpCode = httpCode,
            isSuccess = isSuccess
        )

        val current = _navigationTraces.value.toMutableList()
        if (current.size >= 250) {
            current.removeAt(0)
        }
        current.add(trace)
        _navigationTraces.value = current

        if (domain.isNotEmpty() && isSuccess) {
            val visits = _domainVisits.value.toMutableMap()
            visits[domain] = (visits[domain] ?: 0) + 1
            _domainVisits.value = visits
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun clear() {
        _navigationTraces.value = emptyList()
        _domainVisits.value = emptyMap()
    }
}
