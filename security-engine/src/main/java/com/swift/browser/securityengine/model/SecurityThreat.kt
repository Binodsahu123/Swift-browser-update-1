package com.swift.browser.securityengine.model

enum class ThreatSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class SecurityThreat(
    val threatId: String = java.util.UUID.randomUUID().toString(),
    val targetUrl: String = "",
    val category: String = "Phishing/Malware",
    val severity: ThreatSeverity = ThreatSeverity.HIGH,
    val patternMatched: String = "",
    val detectedAt: Long = System.currentTimeMillis()
)
