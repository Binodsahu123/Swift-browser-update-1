package com.swift.browser.securityengine.model

data class SecuritySession(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val whitelistedDomains: Set<String> = emptySet(),
    val safeBrowsingMode: String = "Enhanced",
    val activeWarningsCount: Int = 0,
    val sessionStartedAt: Long = System.currentTimeMillis()
)
