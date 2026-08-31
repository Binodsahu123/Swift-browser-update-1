package com.swift.browser.securityengine.manager

import com.swift.browser.securityengine.util.SecurityUtils

class SecurityRiskManager {
    fun calculateRiskScore(url: String, isHttps: Boolean, isBlacklisted: Boolean): Int {
        if (SecurityUtils.isLocalOrInternalUrl(url)) return 0
        var score = 0
        if (!isHttps) score += 40
        if (isBlacklisted) score += 100
        val host = SecurityUtils.extractHost(url)
        if (host.count { it == '.' } > 3) score += 20 // Subdomain clutter risk
        return score.coerceIn(0, 100)
    }
}
