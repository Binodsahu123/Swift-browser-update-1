package com.swift.browser.extensionengine

import org.json.JSONObject

class TabBridge(
    private val tabMessenger: TabMessenger,
    private val activeTabManager: ActiveTabManager,
    private val portManager: PortManager
) {
    fun sendMessage(extensionId: String, tabId: String, message: JSONObject, callbackId: String? = null) {
        tabMessenger.sendMessageToTab(extensionId, tabId, message, callbackId)
    }

    fun connect(extensionId: String, tabId: String, channelId: String, portName: String) {
        portManager.connect(
            extensionId = extensionId,
            channelId = channelId,
            portName = portName,
            senderId = "tab_$tabId"
        )
    }
}
