package com.swift.browser.adblockengine.diagnostics

import com.swift.browser.adblockengine.brave.BraveAdblockAdapter
import com.swift.browser.adblockengine.brave.BraveRuleMatcher

/**
 * Executes mock requests on current matched lists to verify block behaviors.
 */
object AdBlockRuleTestRunner {
    fun runUrlTest(url: String, isThirdParty: Boolean, resourceType: String?, documentUrl: String?): String {
        val result = BraveAdblockAdapter.evaluate(url, isThirdParty, resourceType, documentUrl)
        return when (result) {
            BraveRuleMatcher.MatchResult.BLOCK -> "BLOCKED"
            BraveRuleMatcher.MatchResult.EXCEPTION -> "ALLOWED_EXCEPT_WHITELIST"
            BraveRuleMatcher.MatchResult.ALLOW -> "ALLOWED_NO_MATCH"
        }
    }
}
