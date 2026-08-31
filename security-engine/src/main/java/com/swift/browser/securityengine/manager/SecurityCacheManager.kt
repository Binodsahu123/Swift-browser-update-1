package com.swift.browser.securityengine.manager

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class SecurityCacheManager {
    private val urlSafetyCache = ConcurrentHashMap<String, Boolean>()
    private val privateUrlSafetyCache = ConcurrentHashMap<String, Boolean>()

    private fun normalizeKey(url: String, isPrivate: Boolean = false): String {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: ""
            if (isPrivate) {
                if (host.isNotEmpty()) host else url.substringBefore("/").substringBefore("?").substringBefore("#")
            } else {
                val path = uri.path ?: ""
                if (host.isNotEmpty()) "$host$path" else url.substringBefore("?").substringBefore("#")
            }
        } catch (e: Exception) {
            if (isPrivate) {
                url.substringBefore("/").substringBefore("?").substringBefore("#")
            } else {
                url.substringBefore("?").substringBefore("#")
            }
        }
    }

    fun getCachedSafety(url: String, isPrivate: Boolean = false): Boolean? {
        if (isPrivate) {
            return privateUrlSafetyCache[normalizeKey(url, isPrivate = true)]
                ?: urlSafetyCache[normalizeKey(url, isPrivate = true)]
        }
        return urlSafetyCache[normalizeKey(url, isPrivate = false)]
    }

    fun cacheSafety(url: String, isSafe: Boolean, isPrivate: Boolean = false) {
        if (isPrivate) {
            // Private URLs use host-only key or ephemeral private cache so full private URL path is never persisted in normal security cache.
            privateUrlSafetyCache[normalizeKey(url, isPrivate = true)] = isSafe
        } else {
            if (urlSafetyCache.size > 1000) {
                urlSafetyCache.clear()
            }
            urlSafetyCache[normalizeKey(url, isPrivate = false)] = isSafe
        }
    }

    fun clearCache() {
        urlSafetyCache.clear()
        privateUrlSafetyCache.clear()
    }

    fun clearPrivateCache() {
        privateUrlSafetyCache.clear()
    }
}
