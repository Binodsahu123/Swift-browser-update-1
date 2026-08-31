package com.swift.browser.adblockengine.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * Handles fast runtime cached metadata in memory.
 */
object AdBlockLocalCache {
    private val ruleCache = ConcurrentHashMap<String, Boolean>()

    fun cacheResult(key: String, block: Boolean) {
        if (ruleCache.size > 2000) {
            ruleCache.clear()
        }
        ruleCache[key] = block
    }

    fun getResult(key: String): Boolean? {
        return ruleCache[key]
    }

    fun clear() {
        ruleCache.clear()
    }
}
