package com.swift.browser.extensionengine

import org.json.JSONObject

class PortManager(private val messageBus: MessageBus) {
    val registry = PortRegistry()
    val messenger = PortMessenger(messageBus)

    fun connect(
        extensionId: String,
        channelId: String,
        portName: String,
        senderId: String,
        sender: ExtensionSender? = null,
        tabId: String? = null,
        frameId: Int? = null
    ) {
        val connection = PortConnection(
            channelId = channelId,
            name = portName,
            senderId = senderId,
            targetId = extensionId,
            sender = sender ?: ExtensionSender(extensionId = senderId),
            tabId = tabId ?: sender?.tabId,
            frameId = frameId ?: sender?.frameId
        )
        registry.register(connection)
        messenger.sendConnect(extensionId, channelId, portName, senderId)
    }

    fun postMessage(channelId: String, message: JSONObject) {
        val conn = registry.get(channelId)
        if (conn != null && !conn.isDisconnected) {
            messenger.postMessage(channelId, message)
        }
    }

    fun disconnect(channelId: String) {
        val conn = registry.remove(channelId)
        if (conn != null) {
            conn.isDisconnected = true
            messenger.sendDisconnect(channelId)
        }
    }

    fun cleanupForTab(tabId: String) {
        val toDisconnect = registry.getAllActive().filter { it.tabId == tabId || it.sender?.tabId == tabId }
        toDisconnect.forEach { disconnect(it.channelId) }
    }

    fun cleanupForFrame(tabId: String, frameId: Int) {
        val toDisconnect = registry.getAllActive().filter {
            (it.tabId == tabId || it.sender?.tabId == tabId) && (it.frameId == frameId || it.sender?.frameId == frameId)
        }
        toDisconnect.forEach { disconnect(it.channelId) }
    }

    fun cleanupForExtension(extensionId: String) {
        val toDisconnect = registry.getAllActive().filter {
            it.targetId == extensionId || it.senderId == extensionId || it.sender?.extensionId == extensionId
        }
        toDisconnect.forEach { disconnect(it.channelId) }
    }
}
