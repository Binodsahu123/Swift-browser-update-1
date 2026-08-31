package com.swift.browser.adblockengine.brave

/**
 * Connects parsed rules, the rule matcher, and the browser request interceptor pipeline.
 */
object BraveFilterBridge {
    fun applyParsedRules(rules: Collection<BraveRule>) {
        BraveAdblockAdapter.updateRules(rules)
    }

    fun getRulesCount(): Int {
        return BraveAdblockAdapter.getRulesCount()
    }
}
