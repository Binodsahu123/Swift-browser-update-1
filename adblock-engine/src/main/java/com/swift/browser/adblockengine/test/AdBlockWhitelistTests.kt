package com.swift.browser.adblockengine.test

import com.swift.browser.adblockengine.brave.BraveRuleMatcher
import com.swift.browser.adblockengine.brave.BraveRuleParser

/**
 * Validates domain exclusion and exceptions.
 */
object AdBlockWhitelistTests {
    fun runSuite(): Boolean {
        BraveRuleMatcher.clear()

        // Create a rule that only applies to third party or specific domain exclusion
        val rule = BraveRuleParser.parseLine("||ads.com^\$domain=~safehost.com") ?: return false
        BraveRuleMatcher.addRules(listOf(rule))

        // On safehost.com, the rule should not apply (returns ALLOW)
        val result1 = BraveRuleMatcher.evaluate("https://ads.com/popup.js", isThirdParty = true, resourceType = "script", documentHost = "safehost.com")
        if (result1 != BraveRuleMatcher.MatchResult.ALLOW) return false

        // On other host, the rule should apply (returns BLOCK)
        val result2 = BraveRuleMatcher.evaluate("https://ads.com/popup.js", isThirdParty = true, resourceType = "script", documentHost = "badhost.com")
        if (result2 != BraveRuleMatcher.MatchResult.BLOCK) return false

        return true
    }
}
