package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Maps and generates thread-safe, deterministic, and correlated request IDs
 * across the request interception and event lifecycle.
 */
class WebRequestIdMapper {
    private val counter = AtomicLong(1000L)
    private val activeRequestMap = ConcurrentHashMap<String, Long>()

    /**
     * Generates a new unique monotonic request ID.
     */
    fun nextRequestId(): String {
        return counter.incrementAndGet().toString()
    }

    /**
     * Associates an external or internal key with a request ID.
     */
    fun bindRequest(key: String, requestId: String) {
        requestId.toLongOrNull()?.let {
            activeRequestMap[key] = it
        }
    }

    /**
     * Retrieves the request ID for a given key, or generates a new one if not present.
     */
    fun getOrCreateRequestId(key: String): String {
        val existing = activeRequestMap[key]
        if (existing != null) return existing.toString()
        val newId = nextRequestId()
        activeRequestMap[key] = newId.toLong()
        return newId
    }

    /**
     * Cleans up request mapping when a request completes or errors out.
     */
    fun releaseRequest(key: String) {
        activeRequestMap.remove(key)
    }

    /**
     * Clears all request mappings.
     */
    fun clear() {
        activeRequestMap.clear()
    }
}
