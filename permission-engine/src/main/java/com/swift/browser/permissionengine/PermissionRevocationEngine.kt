package com.swift.browser.permissionengine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PermissionRevocationEngine {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun revokePermission(
        origin: String,
        permissionType: String,
        repository: SitePermissionRepository,
        onComplete: () -> Unit = {}
    ) {
        val cleanPerm = permissionType.uppercase()
        // Evict from caches
        PermissionCache.evictFromCache(origin, cleanPerm)
        PermissionGrantEngine.cancelPendingTransactionsForOrigin(origin)
        
        scope.launch {
            try {
                repository.deletePermission(origin, cleanPerm)
                PermissionLogger.logEvent(origin, cleanPerm, "REVOKED", "Successfully deleted from DB and Cache.")
                
                // Add event to diagnostics
                PermissionDiagnostics.recordEvent(
                    PermissionEventModel(
                        eventId = "evt_rev_" + System.nanoTime(),
                        requestId = "manual_revoke",
                        stage = "REVOKED",
                        status = "SUCCESS",
                        reason = "Permission manually revoked",
                        fileName = "PermissionRevocationEngine.kt",
                        className = "PermissionRevocationEngine",
                        methodName = "revokePermission",
                        callbackName = "N/A",
                        details = "Revoked $cleanPerm for origin $origin"
                    )
                )
                
                onComplete()
            } catch (e: Exception) {
                PermissionLogger.logFailure(origin, cleanPerm, "Failed DB revocation", e.toString())
            }
        }
    }

    fun revokeAllPermissions(repository: SitePermissionRepository, onComplete: () -> Unit = {}) {
        PermissionCache.clearSessionCache()
        PermissionCache.clearPersistentCache()
        PermissionGrantEngine.cancelAllPendingTransactions()
        
        scope.launch {
            try {
                repository.clearAll()
                PermissionLogger.logEvent("All_Origins", "All_Permissions", "REVOKED_ALL", "Successfully wiped all site permissions.")
                onComplete()
            } catch (e: Exception) {
                PermissionLogger.logFailure("All_Origins", "All_Permissions", "Failed wiping permissions DB", e.toString())
            }
        }
    }
}
