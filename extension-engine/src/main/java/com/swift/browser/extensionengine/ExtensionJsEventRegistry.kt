package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Generation-aware Event Registry for Extension JS Bridge.
 * Tracks event listener subscriptions by (extensionId, runtimeGenerationId, eventName, listenerId).
 */
class ExtensionJsEventRegistry(
    private val eventManager: EventManager,
    private val webRequestAdapter: ExtensionWebRequestAdapter? = null
) {

    data class EventSubscription(
        val extensionId: String,
        val runtimeGenerationId: String,
        val eventName: String,
        val listenerId: String,
        val filter: JSONObject? = null,
        val extraInfoSpec: JSONArray? = null
    )

    // Key: "extId_eventName_listenerId" -> Subscription
    private val subscriptions = ConcurrentHashMap<String, EventSubscription>()

    fun addListener(
        extensionId: String,
        runtimeGenerationId: String,
        eventName: String,
        listenerId: String,
        filter: JSONObject? = null,
        extraInfoSpec: JSONArray? = null
    ) {
        if (eventName.isBlank() || extensionId.isBlank() || listenerId.isBlank()) return

        val key = "${extensionId}_${eventName}_${listenerId}"
        val sub = EventSubscription(extensionId, runtimeGenerationId, eventName, listenerId, filter, extraInfoSpec)
        subscriptions[key] = sub

        // Register in EventManager
        eventManager.addListener(eventName, extensionId)

        // WebRequest event special handling
        if (eventName.startsWith("webRequest.") && webRequestAdapter != null) {
            val extraSpecs = mutableListOf<String>()
            if (extraInfoSpec != null) {
                for (i in 0 until extraInfoSpec.length()) {
                    extraSpecs.add(extraInfoSpec.optString(i, ""))
                }
            }
            val webFilter = WebRequestFilter.fromJsonObject(filter)
            webRequestAdapter.subscriptionRegistry.addSubscription(extensionId, eventName, webFilter, extraSpecs)
        }
    }

    fun removeListener(
        extensionId: String,
        eventName: String,
        listenerId: String
    ) {
        if (eventName.isBlank() || extensionId.isBlank()) return

        if (listenerId.isNotBlank()) {
            val key = "${extensionId}_${eventName}_${listenerId}"
            subscriptions.remove(key)
        } else {
            // If no specific listener ID provided, remove all matching listeners for this extension + eventName
            val keysToRemove = subscriptions.keys.filter { it.startsWith("${extensionId}_${eventName}_") }
            for (k in keysToRemove) {
                subscriptions.remove(k)
            }
        }

        // Check if extension still has active listeners for this eventName
        val remaining = subscriptions.values.any { it.extensionId == extensionId && it.eventName == eventName }
        if (!remaining) {
            eventManager.removeListener(eventName, extensionId)
            if (eventName.startsWith("webRequest.") && webRequestAdapter != null) {
                webRequestAdapter.subscriptionRegistry.removeSubscription(extensionId, eventName)
            }
        }
    }

    fun removeAllForGeneration(extensionId: String, runtimeGenerationId: String) {
        val keysToRemove = subscriptions.entries
            .filter { it.value.extensionId == extensionId && it.value.runtimeGenerationId == runtimeGenerationId }
            .map { it.key }

        for (k in keysToRemove) {
            val sub = subscriptions.remove(k) ?: continue
            val remaining = subscriptions.values.any { it.extensionId == sub.extensionId && it.eventName == sub.eventName }
            if (!remaining) {
                eventManager.removeListener(sub.eventName, sub.extensionId)
                if (sub.eventName.startsWith("webRequest.") && webRequestAdapter != null) {
                    webRequestAdapter.subscriptionRegistry.removeSubscription(sub.extensionId, sub.eventName)
                }
            }
        }
    }

    fun cleanupExtensionState(extensionId: String) {
        val keysToRemove = subscriptions.keys.filter { it.startsWith("${extensionId}_") }
        for (k in keysToRemove) {
            val sub = subscriptions.remove(k) ?: continue
            eventManager.removeListener(sub.eventName, extensionId)
            if (sub.eventName.startsWith("webRequest.") && webRequestAdapter != null) {
                webRequestAdapter.subscriptionRegistry.removeSubscription(extensionId, sub.eventName)
            }
        }
    }
}
