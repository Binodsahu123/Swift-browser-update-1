package com.swift.browser.extensionengine

import org.json.JSONObject

data class ExtensionMessage(
    val messageId: String,
    val extensionId: String,
    val targetExtensionId: String,
    val sender: ExtensionSender,
    val targetContext: String? = null,
    val payload: JSONObject,
    val createdAt: Long = System.currentTimeMillis(),
    val deadline: Long = System.currentTimeMillis() + 30000L, // 30 seconds default timeout
    val privateSessionId: String? = null,
    val runtimeGenerationId: String? = null,
    val callbackId: String? = null,
    val expectsResponse: Boolean = false
)
