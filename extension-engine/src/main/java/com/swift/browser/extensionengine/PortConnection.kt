package com.swift.browser.extensionengine

import org.json.JSONObject

data class PortConnection(
    val channelId: String,
    val name: String,
    val senderId: String,
    val targetId: String,
    val sender: ExtensionSender? = null,
    val tabId: String? = null,
    val frameId: Int? = null,
    var isDisconnected: Boolean = false
) {
    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("channelId", channelId)
            put("name", name)
            put("senderId", senderId)
            put("targetId", targetId)
            if (sender != null) {
                put("sender", sender.toJSONObject())
            }
            if (tabId != null) put("tabId", tabId)
            if (frameId != null) put("frameId", frameId)
            put("isDisconnected", isDisconnected)
        }
    }
}
