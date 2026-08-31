package com.swift.browser.analyticscore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrowserAnalyticsManager {
    private val _events = MutableStateFlow<List<BrowserAnalyticsEvent>>(emptyList())
    val events: StateFlow<List<BrowserAnalyticsEvent>> = _events.asStateFlow()

    private val _totalPageLoads = MutableStateFlow(0)
    val totalPageLoads: StateFlow<Int> = _totalPageLoads.asStateFlow()

    private val _totalSearches = MutableStateFlow(0)
    val totalSearches: StateFlow<Int> = _totalSearches.asStateFlow()

    fun trackPageLoad(url: String, isIncognito: Boolean = false, context: AnalyticsContext = if (isIncognito) AnalyticsContext.PRIVATE else AnalyticsContext.NORMAL) {
        val isPrivate = context.isPrivate || isIncognito
        _totalPageLoads.value += 1
        logEvent(
            eventName = "page_load",
            params = if (isPrivate) {
                mapOf(
                    "url" to "[PRIVATE_BROWSING]",
                    "is_incognito" to true
                )
            } else {
                mapOf(
                    "url" to PrivacyTelemetryManager.sanitizeUrl(url),
                    "is_incognito" to false
                )
            },
            context = context
        )
    }

    fun trackTabCreated(tabId: String, isIncognito: Boolean, context: AnalyticsContext = if (isIncognito) AnalyticsContext.PRIVATE else AnalyticsContext.NORMAL) {
        val isPrivate = context.isPrivate || isIncognito
        logEvent(
            eventName = "tab_created",
            params = mapOf("tab_id" to tabId, "is_incognito" to isPrivate),
            context = context
        )
    }

    fun trackTabClosed(tabId: String, context: AnalyticsContext = AnalyticsContext.NORMAL) {
        logEvent(
            eventName = "tab_closed",
            params = mapOf("tab_id" to tabId),
            context = context
        )
    }

    fun trackSearchQuery(query: String, searchEngine: String, context: AnalyticsContext = AnalyticsContext.NORMAL) {
        _totalSearches.value += 1
        if (context.isPrivate) {
            // Safe aggregate runtime metrics continue with zero private search text or length leakage
            logEvent(
                eventName = "search_query",
                params = mapOf(
                    "engine" to searchEngine,
                    "is_incognito" to true
                ),
                context = context
            )
        } else {
            logEvent(
                eventName = "search_query",
                params = mapOf(
                    "query_length" to query.length,
                    "engine" to searchEngine,
                    "is_incognito" to false
                ),
                context = context
            )
        }
    }

    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap(), context: AnalyticsContext = AnalyticsContext.NORMAL) {
        val event = BrowserAnalyticsEvent(eventName = eventName, params = params)
        val current = _events.value.toMutableList()
        if (current.size >= 300) {
            current.removeAt(0)
        }
        current.add(event)
        _events.value = current
    }

    fun clearHistory() {
        _events.value = emptyList()
    }
}

