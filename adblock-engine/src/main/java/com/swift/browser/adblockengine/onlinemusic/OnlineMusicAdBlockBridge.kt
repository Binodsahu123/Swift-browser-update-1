package com.swift.browser.adblockengine.onlinemusic

import com.swift.browser.adblockengine.core.AdBlockRequestDecision
import com.swift.browser.adblockengine.core.AdBlockRuleEngine

/**
 * Main adapter bridge connecting the browser's adblock subsystem with the Online Music Player's requests.
 */
object OnlineMusicAdBlockBridge {

    fun shouldBlockMusicRequest(url: String, documentUrl: String?): Boolean {
        // Evaluate through rule engine
        val decision = AdBlockRuleEngine.evaluate(url, isThirdParty = true, resourceType = "media", documentUrl = documentUrl)
        if (decision == AdBlockRequestDecision.BLOCK) {
            // Apply music domain safeguards
            return OnlineMusicRequestPolicy.shouldBlockInMusicScope(url)
        }
        return false
    }
}
