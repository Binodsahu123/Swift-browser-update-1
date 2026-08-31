package com.swift.browser.adblockengine.test

import com.swift.browser.adblockengine.brave.BraveRuleParser

/**
 * Validates core rule parsed outcomes and syntax options.
 */
object AdBlockUnitTests {
    fun runSuite(): Boolean {
        // Test 1: Simple cosmetic rule parse
        val cosmeticRule = BraveRuleParser.parseLine("example.com##.ad-banner")
        if (cosmeticRule == null || !cosmeticRule.isCosmetic || cosmeticRule.elementSelector != ".ad-banner") {
            return false
        }

        // Test 2: Standard block option parse
        val scriptRule = BraveRuleParser.parseLine("||doubleclick.net^\$script,third-party")
        if (scriptRule == null || !scriptRule.isScriptOnly || !scriptRule.isThirdPartyOnly) {
            return false
        }

        return true
    }
}
