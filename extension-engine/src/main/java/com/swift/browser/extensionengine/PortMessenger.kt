package com.swift.browser.extensionengine

import org.json.JSONObject

class PortMessenger(private val messageBus: MessageBus) {

    fun sendConnect(extensionId: String, channelId: String, portName: String, senderId: String) {
        messageBus.broadcastPortConnect(extensionId, channelId, portName, senderId)
    }

    fun postMessage(channelId: String, message: JSONObject) {
        messageBus.broadcastPortMessage(channelId, message)
    }

    fun sendDisconnect(channelId: String) {
        messageBus.broadcastPortDisconnect(channelId)
    }
}
