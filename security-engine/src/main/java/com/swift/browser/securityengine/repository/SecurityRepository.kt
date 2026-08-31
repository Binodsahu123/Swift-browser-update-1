package com.swift.browser.securityengine.repository

import java.util.Collections

class SecurityRepository {
    private val whitelistedSslDomains = Collections.synchronizedSet(mutableSetOf<String>())
    private val blacklistedPatterns = Collections.synchronizedList(mutableListOf(
        "malware-example.com",
        "phishing-test.org",
        "suspect-site.net",
        "update-chrome-security.com",
        "login-paypal-security",
        "verify-visa-card",
        "free-gift-rewards.top",
        "cryptoclaim",
        "bank-security-alert"
    ))

    fun whitelistDomain(host: String) {
        if (host.isNotBlank()) {
            whitelistedSslDomains.add(host.lowercase())
        }
    }

    fun unwhitelistDomain(host: String) {
        whitelistedSslDomains.remove(host.lowercase())
    }

    fun isDomainWhitelisted(host: String): Boolean {
        return whitelistedSslDomains.contains(host.lowercase())
    }

    fun getWhitelistedDomains(): Set<String> {
        return whitelistedSslDomains.toSet()
    }

    fun getBlacklistedPatterns(): List<String> {
        return blacklistedPatterns.toList()
    }

    fun addBlacklistedPattern(pattern: String) {
        if (pattern.isNotBlank() && !blacklistedPatterns.contains(pattern)) {
            blacklistedPatterns.add(pattern)
        }
    }
}
