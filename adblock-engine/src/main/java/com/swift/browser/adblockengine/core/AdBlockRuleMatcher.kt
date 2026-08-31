package com.swift.browser.adblockengine.core

import com.swift.browser.adblockengine.brave.BraveRuleMatcher

/**
 * Dispatches matching operations on active rules. Delegates directly to high-performance matcher logic.
 */
object AdBlockRuleMatcher {
    fun matches(
        url: String,
        reqHost: String,
        isThirdParty: Boolean,
        resourceType: String?,
        documentHost: String?
    ): Boolean {
        return BraveRuleMatcher.matchesRequest(url, reqHost, isThirdParty, resourceType, documentHost)
    }
}
