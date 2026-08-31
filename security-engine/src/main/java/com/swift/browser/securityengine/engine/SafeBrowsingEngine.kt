package com.swift.browser.securityengine.engine

import android.util.Log
import com.swift.browser.securityengine.manager.SecurityCacheManager
import com.swift.browser.securityengine.manager.SecurityRepositoryManager
import com.swift.browser.securityengine.util.SecurityUtils

class SafeBrowsingEngine(
    private val repoManager: SecurityRepositoryManager,
    private val cacheManager: SecurityCacheManager
) {
    private companion object {
        const val TAG = "SafeBrowsingEngine"
    }

    fun isUrlSafe(url: String, safeBrowsingMode: String = "Enhanced", isPrivate: Boolean = false): Boolean {
        if (safeBrowsingMode == "No" || SecurityUtils.isLocalOrInternalUrl(url)) {
            return true
        }

        cacheManager.getCachedSafety(url, isPrivate)?.let { return it }

        val lowerUrl = url.lowercase()
        val blacklisted = repoManager.getBlacklistedPatterns()
        for (pattern in blacklisted) {
            if (lowerUrl.contains(pattern)) {
                Log.w(TAG, "Safe Browsing blocked URL matching pattern: $pattern")
                cacheManager.cacheSafety(url, false, isPrivate)
                return false
            }
        }

        cacheManager.cacheSafety(url, true, isPrivate)
        return true
    }
}
