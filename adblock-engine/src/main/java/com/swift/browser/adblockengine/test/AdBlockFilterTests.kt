package com.swift.browser.adblockengine.test

import com.swift.browser.adblockengine.brave.BraveRuleMatcher
import com.swift.browser.adblockengine.brave.BraveRuleParser

/**
 * Validates complex filter match evaluations.
 */
object AdBlockFilterTests {
    fun runSuite(): Boolean {
        BraveRuleMatcher.clear()
        
        val rule1 = BraveRuleParser.parseLine("||eviltracker.com^") ?: return false
        val rule2 = BraveRuleParser.parseLine("@@||eviltracker.com/safe^") ?: return false
        
        BraveRuleMatcher.addRules(listOf(rule1, rule2))

        val result1 = BraveRuleMatcher.evaluate("https://eviltracker.com/ads/pixel.png", isThirdParty = true, resourceType = "image", documentHost = "google.com")
        if (result1 != BraveRuleMatcher.MatchResult.BLOCK) return false

        val result2 = BraveRuleMatcher.evaluate("https://eviltracker.com/safe/script.js", isThirdParty = true, resourceType = "script", documentHost = "google.com")
        if (result2 != BraveRuleMatcher.MatchResult.EXCEPTION) return false

        return true
    }
}
