package com.swift.browser.adblockengine.diagnostics

import com.swift.browser.adblockengine.brave.BraveAdblockAdapter
import com.swift.browser.adblockengine.core.AdBlockStatsManager

/**
 * Diagnostic data state bundle for rendering debug status pages.
 */
object AdBlockDebugScreen {
    fun getDebugInfo(): String {
        return """
            --- SWIFT AD BLOCK ENGINE DIAGNOSTICS ---
            Compiled Rules Active: ${BraveAdblockAdapter.getRulesCount()}
            Blocked Network Ads: ${AdBlockStatsManager.getAdsBlocked()}
            Blocked Trackers: ${AdBlockStatsManager.getTrackersBlocked()}
            Cosmetic Element Hides: ${AdBlockStatsManager.getCosmeticHides()}
            Total blocked elements: ${AdBlockStatsManager.getTotalBlocked()}
            --- END OF REPORT ---
        """.trimIndent()
    }
}
