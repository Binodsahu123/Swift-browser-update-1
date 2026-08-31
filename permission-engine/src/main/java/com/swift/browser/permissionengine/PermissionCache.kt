package com.swift.browser.permissionengine

import java.util.concurrent.ConcurrentHashMap

object PermissionCache {
    // Keeps track of "ALLOW_ONCE" or session-lifetime decisions
    private val sessionCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    // Keeps track of the loaded persistent decisions
    private val persistentCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    // Isolated in-memory cache for incognito tabs only
    private val incognitoCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    fun getCachedDecision(origin: String, permissionType: String, isIncognito: Boolean = false): String? {
        val clean = cleanOrigin(origin)
        if (isIncognito) {
            // Incognito NEVER reads persistentCache or public sessionCache
            return incognitoCache[clean]?.get(permissionType.uppercase())
        }
        // Session decisions override persistent ones (e.g. temporary overrides)
        val sessionState = sessionCache[clean]?.get(permissionType.uppercase())
        if (sessionState != null) {
            return sessionState
        }
        return persistentCache[clean]?.get(permissionType.uppercase())
    }

    fun cacheIncognitoDecision(origin: String, permissionType: String, decision: String) {
        val clean = cleanOrigin(origin)
        incognitoCache.getOrPut(clean) { ConcurrentHashMap() }[permissionType.uppercase()] = decision.uppercase()
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = "cache_incognito",
                stage = "CACHE_UPDATE",
                status = "SUCCESS",
                reason = "Cached incognito decision: $decision",
                fileName = "PermissionCache.kt",
                className = "PermissionCache",
                methodName = "cacheIncognitoDecision",
                callbackName = "N/A",
                details = "Cached incognito $permissionType for origin $clean with state $decision"
            )
        )
    }

    fun cacheSessionDecision(origin: String, permissionType: String, decision: String) {
        val clean = cleanOrigin(origin)
        sessionCache.getOrPut(clean) { ConcurrentHashMap() }[permissionType.uppercase()] = decision.uppercase()
        
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = "cache_session",
                stage = "CACHE_UPDATE",
                status = "SUCCESS",
                reason = "Cached session decision: $decision",
                fileName = "PermissionCache.kt",
                className = "PermissionCache",
                methodName = "cacheSessionDecision",
                callbackName = "N/A",
                details = "Cached $permissionType for origin $clean with state $decision"
            )
        )
    }

    fun cachePersistentDecision(origin: String, permissionType: String, decision: String) {
        val clean = cleanOrigin(origin)
        persistentCache.getOrPut(clean) { ConcurrentHashMap() }[permissionType.uppercase()] = decision.uppercase()
        
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = "cache_persistent",
                stage = "CACHE_UPDATE",
                status = "SUCCESS",
                reason = "Cached persistent decision: $decision",
                fileName = "PermissionCache.kt",
                className = "PermissionCache",
                methodName = "cachePersistentDecision",
                callbackName = "N/A",
                details = "Cached $permissionType for origin $clean with state $decision"
            )
        )
    }

    fun clearIncognitoCache() {
        incognitoCache.clear()
    }

    fun clearSessionCache() {
        sessionCache.clear()
    }

    fun clearPersistentCache() {
        persistentCache.clear()
    }

    fun evictFromCache(origin: String, permissionType: String) {
        val clean = cleanOrigin(origin)
        sessionCache[clean]?.remove(permissionType.uppercase())
        persistentCache[clean]?.remove(permissionType.uppercase())
        incognitoCache[clean]?.remove(permissionType.uppercase())
    }

    private fun cleanOrigin(origin: String): String {
        return OriginNormalizer.normalize(origin)
    }
}
