package com.swift.browser.adblockengine.brave

/**
 * Main adapter between Swift Browser and Brave-style adblock engine.
 */
object BraveAdblockAdapter {

    fun updateRules(rules: Collection<BraveRule>) {
        BraveRuleMatcher.clear()
        BraveRuleMatcher.addRules(rules)
    }

    fun getRulesCount(): Int {
        return BraveRuleMatcher.getRulesCount()
    }

    fun evaluate(
        url: String,
        isThirdParty: Boolean,
        resourceType: String?,
        documentUrl: String?
    ): BraveRuleMatcher.MatchResult {
        val docHost = if (documentUrl != null) {
            try {
                val uri = android.net.Uri.parse(documentUrl)
                uri.host?.lowercase() ?: ""
            } catch (e: Exception) {
                null
            }
        } else null

        return BraveRuleMatcher.evaluate(url, isThirdParty, resourceType, docHost)
    }

    fun getCosmeticSelectors(documentUrl: String?): List<String> {
        val docHost = if (documentUrl != null) {
            try {
                val uri = android.net.Uri.parse(documentUrl)
                uri.host?.lowercase() ?: ""
            } catch (e: Exception) {
                null
            }
        } else null

        return BraveRuleMatcher.getCosmeticSelectors(docHost)
    }
}
