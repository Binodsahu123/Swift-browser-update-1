package com.swift.browser.securityengine.model

data class SecurityError(
    val errorCode: Int = -1,
    val description: String = "",
    val failingUrl: String = "",
    val isFatal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
