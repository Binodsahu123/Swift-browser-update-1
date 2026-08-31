package com.swift.browser.extensionengine

import org.json.JSONObject

data class PendingMessage(
    val messageId: String,
    val callbackId: String,
    val extensionId: String,
    val runtimeGenerationId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val deadline: Long,
    val onResponse: (Any?, String?) -> Unit
)
