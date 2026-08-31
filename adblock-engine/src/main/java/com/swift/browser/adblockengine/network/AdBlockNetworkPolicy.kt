package com.swift.browser.adblockengine.network

import com.swift.browser.adblockengine.core.AdBlockPolicy

/**
 * Handles system policies relating to deep network request interceptions.
 */
object AdBlockNetworkPolicy {
    fun shouldProcessRequest(url: String): Boolean {
        // Skip browser UI scheme, empty protocols or about/blank
        if (url.startsWith("swift://") || url.startsWith("about:") || url.startsWith("chrome:") || url.startsWith("file:")) {
            return false
        }
        return AdBlockPolicy.trackerBlockingEnabled || AdBlockPolicy.isEasyListEnabled
    }
}
