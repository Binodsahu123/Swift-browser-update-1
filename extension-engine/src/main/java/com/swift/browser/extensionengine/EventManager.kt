package com.swift.browser.extensionengine

import org.json.JSONObject

class EventManager(private val messageBus: MessageBus) {

    private val eventListeners = mutableMapOf<String, MutableList<String>>()
    var serviceWorkerEventDispatcher: ServiceWorkerEventDispatcher? = null

    fun addListener(eventName: String, extensionId: String) {
        val list = eventListeners.getOrPut(eventName) { mutableListOf() }
        if (!list.contains(extensionId)) {
            list.add(extensionId)
        }
    }

    fun removeListener(eventName: String, extensionId: String) {
        eventListeners[eventName]?.remove(extensionId)
    }

    fun hasListener(eventName: String, extensionId: String): Boolean {
        return eventListeners[eventName]?.contains(extensionId) == true
    }

    /**
     * Publishes a browser lifecycle event only to a specific extension,
     * preventing any other extension from receiving it.
     */
    fun triggerEventForExtension(extensionId: String, eventName: String, params: JSONObject) {
        val swDispatcher = serviceWorkerEventDispatcher
        if (swDispatcher != null) {
            swDispatcher.dispatchRuntimeEvent(extensionId, eventName, params)
        } else {
            val message = JSONObject().apply {
                put("type", "EVENT_DISPATCH")
                put("eventName", eventName)
                put("data", params)
                put("__targetExtensionId__", extensionId)
            }
            messageBus.broadcastMessage(extensionId, null, message)
        }
    }

    /**
     * Publishes a browser lifecycle event (like tabs.onUpdated or webNavigation)
     * down to subscribed background or content scripts.
     */
    fun triggerEvent(eventName: String, params: JSONObject) {
        val registrants = eventListeners[eventName] ?: return
        for (extId in registrants) {
            triggerEventForExtension(extId, eventName, params)
        }
    }
}
