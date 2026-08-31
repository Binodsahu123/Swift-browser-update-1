package com.swift.browser.desktopengine.rules

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class DesktopCompatibilityRepository(private val context: Context? = null) {
    private val cachedRules = ConcurrentHashMap<String, DesktopSiteRule>()

    init {
        preloadDefaultRules()
    }

    private fun preloadDefaultRules() {
        val rules = listOf(
            DesktopSiteRule(
                domain = "facebook.com",
                desktopSubdomainRewrite = "www.facebook.com"
            ),
            DesktopSiteRule(
                domain = "reddit.com",
                desktopSubdomainRewrite = "www.reddit.com",
                customCssOverrides = "body { min-width: 1200px !important; }"
            ),
            DesktopSiteRule(
                domain = "twitter.com",
                desktopSubdomainRewrite = "x.com"
            ),
            DesktopSiteRule(
                domain = "x.com",
                desktopSubdomainRewrite = "x.com"
            ),
            DesktopSiteRule(
                domain = "wikipedia.org",
                customCssOverrides = "#content { margin-left: 10em !important; }"
            )
        )
        for (rule in rules) {
            cachedRules[rule.domain] = rule
        }
    }

    fun getRuleForHost(host: String): DesktopSiteRule? {
        val cleanHost = DesktopHostNormalizer.getCanonicalHost(host)
        return cachedRules[cleanHost] ?: cachedRules.keys.find { cleanHost.endsWith(it) }?.let { cachedRules[it] }
    }

    fun calculateCompatibilityScore(host: String): Int {
        val rule = getRuleForHost(host) ?: return 85
        var score = 90
        if (rule.desktopSubdomainRewrite != null) score += 5
        if (rule.customCssOverrides != null) score += 5
        return score.coerceAtMost(100)
    }
}
