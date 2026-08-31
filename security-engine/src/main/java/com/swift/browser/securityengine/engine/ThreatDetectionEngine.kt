package com.swift.browser.securityengine.engine

import com.swift.browser.securityengine.manager.SecurityRepositoryManager
import com.swift.browser.securityengine.model.SecurityThreat
import com.swift.browser.securityengine.model.ThreatSeverity

import com.swift.browser.securityengine.util.SecurityUtils

class ThreatDetectionEngine(
    private val repoManager: SecurityRepositoryManager
) {
    fun detectThreat(url: String, isPrivate: Boolean = false): SecurityThreat? {
        val lowerUrl = url.lowercase()
        for (pattern in repoManager.getBlacklistedPatterns()) {
            if (lowerUrl.contains(pattern)) {
                val target = if (isPrivate) {
                    val host = SecurityUtils.extractHost(url)
                    if (host.isNotBlank()) "https://$host/[PRIVATE_URL]" else "[PRIVATE_URL]"
                } else {
                    url
                }
                return SecurityThreat(
                    targetUrl = target,
                    category = "Phishing/Malware",
                    severity = ThreatSeverity.CRITICAL,
                    patternMatched = pattern
                )
            }
        }
        return null
    }
}
