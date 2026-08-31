package com.swift.browser.securityengine.model

enum class SecurityStatus {
    SAFE,
    WARNING,
    BLOCKED,
    SSL_WARNING,
    THREAT_DETECTED,
    ANALYZING,
    IDLE,
    ERROR
}

data class SecurityState(
    val status: SecurityStatus = SecurityStatus.IDLE,
    val currentUrl: String = "",
    val riskScore: Int = 0,
    val isProtected: Boolean = true,
    val lastCheckedTimestamp: Long = System.currentTimeMillis(),
    val activeThreatsCount: Int = 0
)
