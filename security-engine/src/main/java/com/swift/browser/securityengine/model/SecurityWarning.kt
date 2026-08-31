package com.swift.browser.securityengine.model

data class SecurityWarning(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "",
    val warningType: String = "UNKNOWN",
    val title: String = "Security Warning",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isBypassAllowed: Boolean = true
)
