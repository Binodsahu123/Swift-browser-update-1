package com.swift.browser.adblockengine.core

import com.swift.browser.adblockengine.brave.BraveAdblockAdapter

/**
 * Orchestrates rule evaluations, matching lists, and caching parsed rule vectors.
 */
object AdBlockRuleEngine {
    
    fun evaluate(
        url: String,
        isThirdParty: Boolean,
        resourceType: String?,
        documentUrl: String?
    ): AdBlockRequestDecision {
        if (!AdBlockEngine.isEnabled()) {
            return AdBlockRequestDecision.ALLOW
        }

        // Check Whitelist overrides
        val docHost = getDomainName(documentUrl)
        if (docHost != null && (AdBlockWhitelistManager.isWhitelisted(docHost) || docHost.contains("youtube.com") || docHost.contains("youtu.be"))) {
            return AdBlockRequestDecision.ALLOW_WITH_EXCEPTION
        }

        // Check Site exceptions blacklist overrides (forces block on blacklisted domains even if engine is turned off)
        val isExplicitlyBlacklisted = docHost != null && AdBlockExceptionManager.isExplicitlyBlacklisted(docHost)

        val result = BraveAdblockAdapter.evaluate(url, isThirdParty, resourceType, documentUrl)
        
        return when (result) {
            com.swift.browser.adblockengine.brave.BraveRuleMatcher.MatchResult.EXCEPTION -> AdBlockRequestDecision.ALLOW_WITH_EXCEPTION
            com.swift.browser.adblockengine.brave.BraveRuleMatcher.MatchResult.BLOCK -> AdBlockRequestDecision.BLOCK
            com.swift.browser.adblockengine.brave.BraveRuleMatcher.MatchResult.ALLOW -> {
                if (isExplicitlyBlacklisted) AdBlockRequestDecision.BLOCK else AdBlockRequestDecision.ALLOW
            }
        }
    }

    private fun getDomainName(url: String?): String? {
        if (url == null || url.startsWith("swift://") || url == "about:blank") return null
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            null
        }
    }
}
