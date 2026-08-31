package com.swift.browser.extensionengine

import org.json.JSONObject

class TabMessenger(private val messageBus: MessageBus) {

    fun sendMessageToTab(extensionId: String, tabId: String, message: JSONObject, callbackId: String? = null) {
        // Broadcasts specifically targeting a given tabId
        messageBus.broadcastMessage(
            extensionId = extensionId,
            senderTabId = null,
            message = message,
            callbackId = callbackId,
            targetTabId = tabId
        )
    }

    fun broadcastToAllTabs(extensionId: String, message: JSONObject) {
        messageBus.broadcastMessage(
            extensionId = extensionId,
            senderTabId = null,
            message = message,
            callbackId = null,
            targetTabId = null
        )
    }
}
