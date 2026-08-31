package com.swift.browser.extensionengine

import com.swift.browser.permissionengine.ExtensionHostPatternMatcher
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Filter criteria for chrome.webRequest event listeners.
 */
data class WebRequestFilter(
    val urls: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val tabId: Int? = null,
    val windowId: Int? = null
) {
    fun matches(request: ExtensionNetworkRequestContext): Boolean {
        // TabId filter
        if (tabId != null && tabId != -1 && request.tabId != -1 && tabId != request.tabId) {
            return false
        }

        // Resource type filter
        if (types.isNotEmpty()) {
            val reqType = request.resourceType.lowercase()
            val matchesType = types.any { it.equals(reqType, ignoreCase = true) }
            if (!matchesType) return false
        }

        // URLs pattern filter
        if (urls.isNotEmpty()) {
            val matchesUrl = urls.any { pattern ->
                ExtensionHostPatternMatcher.matches(pattern, request.url)
            }
            if (!matchesUrl) return false
        }

        return true
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): WebRequestFilter {
            if (obj == null) return WebRequestFilter()
            val urls = mutableListOf<String>()
            val urlsArr = obj.optJSONArray("urls")
            if (urlsArr != null) {
                for (i in 0 until urlsArr.length()) {
                    urls.add(urlsArr.getString(i))
                }
            }

            val types = mutableListOf<String>()
            val typesArr = obj.optJSONArray("types")
            if (typesArr != null) {
                for (i in 0 until typesArr.length()) {
                    types.add(typesArr.getString(i))
                }
            }

            val tabId = if (obj.has("tabId")) obj.getInt("tabId") else null
            val windowId = if (obj.has("windowId")) obj.getInt("windowId") else null

            return WebRequestFilter(urls = urls, types = types, tabId = tabId, windowId = windowId)
        }
    }
}

/**
 * Representation of an active chrome.webRequest event subscription.
 */
data class WebRequestSubscription(
    val extensionId: String,
    val eventName: String,
    val filter: WebRequestFilter = WebRequestFilter(),
    val extraInfoSpec: List<String> = emptyList()
)

/**
 * Thread-safe registry maintaining chrome.webRequest subscriptions for all extensions.
 */
class WebRequestSubscriptionRegistry {
    // eventName -> list of subscriptions
    private val subscriptions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebRequestSubscription>>()

    /**
     * Adds an event subscription for a given extension.
     */
    fun addSubscription(
        extensionId: String,
        eventName: String,
        filter: WebRequestFilter = WebRequestFilter(),
        extraInfoSpec: List<String> = emptyList()
    ) {
        val cleanEvent = if (eventName.startsWith("webRequest.")) eventName else "webRequest.$eventName"
        val list = subscriptions.getOrPut(cleanEvent) { CopyOnWriteArrayList() }
        // Remove existing identical subscription to avoid duplicates
        list.removeIf { it.extensionId.equals(extensionId, ignoreCase = true) }
        list.add(WebRequestSubscription(extensionId, cleanEvent, filter, extraInfoSpec))
    }

    /**
     * Removes an event subscription for a given extension.
     */
    fun removeSubscription(extensionId: String, eventName: String) {
        val cleanEvent = if (eventName.startsWith("webRequest.")) eventName else "webRequest.$eventName"
        subscriptions[cleanEvent]?.removeIf { it.extensionId.equals(extensionId, ignoreCase = true) }
    }

    /**
     * Removes all subscriptions for an extension (e.g. on disable or uninstall).
     */
    fun removeAllForExtension(extensionId: String) {
        for ((_, list) in subscriptions) {
            list.removeIf { it.extensionId.equals(extensionId, ignoreCase = true) }
        }
    }

    /**
     * Retrieves matching subscriptions for a given event and request context.
     */
    fun getMatchingSubscriptions(
        eventName: String,
        request: ExtensionNetworkRequestContext
    ): List<WebRequestSubscription> {
        val cleanEvent = if (eventName.startsWith("webRequest.")) eventName else "webRequest.$eventName"
        val list = subscriptions[cleanEvent] ?: return emptyList()
        return list.filter { it.filter.matches(request) }
    }

    /**
     * Checks if any extension is subscribed to a given event.
     */
    fun hasSubscribers(eventName: String): Boolean {
        val cleanEvent = if (eventName.startsWith("webRequest.")) eventName else "webRequest.$eventName"
        return subscriptions[cleanEvent]?.isNotEmpty() == true
    }

    /**
     * Clears all subscriptions.
     */
    fun clear() {
        subscriptions.clear()
    }
}
