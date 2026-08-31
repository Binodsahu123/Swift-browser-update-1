package com.swift.browser.permissionengine

data class PermissionEventModel @JvmOverloads constructor(
    val eventId: String,
    val requestId: String,
    val stage: String, // "REQUEST_RECEIVED", "ANALYZED", "POLICY_RESOLVED", "CACHE_HIT", etc.
    val status: String, // "SUCCESS", "FAILURE", "PENDING"
    val reason: String,
    val fileName: String,
    val className: String,
    val methodName: String,
    val callbackName: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
