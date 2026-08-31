package com.swift.browser.permissionengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative in-memory cache and persistence repository for extension permissions.
 * Bridges Room database storage with low-latency synchronous permission queries.
 */
class ExtensionPermissionRepository(
    private val dao: ExtensionPermissionDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val TAG = "ExtPermissionRepo"

    // extensionId -> Set of granted API permissions (e.g. "tabs", "storage", "history")
    private val normalApiGrants = ConcurrentHashMap<String, MutableSet<String>>()
    private val privateApiGrants = ConcurrentHashMap<String, MutableSet<String>>()

    // extensionId -> Set of granted Host match patterns (e.g. "<all_urls>", "https://*.google.com/*")
    private val normalHostGrants = ConcurrentHashMap<String, MutableSet<String>>()
    private val privateHostGrants = ConcurrentHashMap<String, MutableSet<String>>()

    // extensionId -> allowed in incognito / private mode
    private val allowedInPrivateExtensions = ConcurrentHashMap.newKeySet<String>()

    init {
        loadAllFromDb()
    }

    private fun loadAllFromDb() {
        scope.launch {
            try {
                val allEntities = dao.getAllExtensionPermissions()
                for (entity in allEntities) {
                    val extId = entity.extensionId.lowercase().trim()
                    if (entity.state == "GRANTED") {
                        if (entity.permission == "__PRIVATE_ALLOWED__") {
                            allowedInPrivateExtensions.add(extId)
                            continue
                        }

                        if (entity.scope == "HOST") {
                            val targetMap = if (entity.isPrivateScope) privateHostGrants else normalHostGrants
                            targetMap.computeIfAbsent(extId) { ConcurrentHashMap.newKeySet() }.add(entity.permission)
                        } else {
                            val targetMap = if (entity.isPrivateScope) privateApiGrants else normalApiGrants
                            targetMap.computeIfAbsent(extId) { ConcurrentHashMap.newKeySet() }.add(entity.permission.lowercase().trim())
                        }
                    }
                }
                Log.d(TAG, "Extension permissions initialized from Room. Count: ${allEntities.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading extension permissions from database", e)
            }
        }
    }

    fun hasApiPermission(extensionId: String, permission: String, isPrivate: Boolean = false): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        val cleanPerm = permission.lowercase().trim()
        if (cleanExtId.isBlank() || cleanPerm.isBlank()) return false

        // Private mode policy gate
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return false

        val apiSet = if (isPrivate) {
            privateApiGrants[cleanExtId] ?: normalApiGrants[cleanExtId]
        } else {
            normalApiGrants[cleanExtId]
        }

        return apiSet?.contains(cleanPerm) == true
    }

    fun hasHostPermission(extensionId: String, url: String, isPrivate: Boolean = false): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        if (cleanExtId.isBlank() || url.isBlank()) return false

        // Private mode policy gate
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return false

        val hostSet = if (isPrivate) {
            privateHostGrants[cleanExtId] ?: normalHostGrants[cleanExtId]
        } else {
            normalHostGrants[cleanExtId]
        }

        if (hostSet.isNullOrEmpty()) return false
        return ExtensionHostPatternMatcher.matchesAny(hostSet, url)
    }

    fun getGrantedApiPermissions(extensionId: String, isPrivate: Boolean = false): Set<String> {
        val cleanExtId = extensionId.lowercase().trim()
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return emptySet()

        val apiSet = if (isPrivate) {
            privateApiGrants[cleanExtId] ?: normalApiGrants[cleanExtId]
        } else {
            normalApiGrants[cleanExtId]
        }
        return apiSet?.toSet() ?: emptySet()
    }

    fun getGrantedHostPatterns(extensionId: String, isPrivate: Boolean = false): Set<String> {
        val cleanExtId = extensionId.lowercase().trim()
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return emptySet()

        val hostSet = if (isPrivate) {
            privateHostGrants[cleanExtId] ?: normalHostGrants[cleanExtId]
        } else {
            normalHostGrants[cleanExtId]
        }
        return hostSet?.toSet() ?: emptySet()
    }

    fun grantPermission(
        extensionId: String,
        permission: String,
        scopeType: String,
        source: String = "OPTIONAL_RUNTIME",
        isPrivate: Boolean = false
    ): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        val cleanPerm = if (scopeType == "HOST") permission.trim() else permission.lowercase().trim()
        if (cleanExtId.isBlank() || cleanPerm.isBlank()) return false

        val targetMap = if (scopeType == "HOST") {
            if (isPrivate) privateHostGrants else normalHostGrants
        } else {
            if (isPrivate) privateApiGrants else normalApiGrants
        }

        targetMap.computeIfAbsent(cleanExtId) { ConcurrentHashMap.newKeySet() }.add(cleanPerm)

        val entity = ExtensionPermissionEntity(
            extensionId = cleanExtId,
            permission = cleanPerm,
            scope = scopeType,
            state = "GRANTED",
            source = source,
            isPrivateScope = isPrivate
        )

        scope.launch {
            try {
                dao.insertOrUpdate(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting granted permission: $cleanPerm for $cleanExtId", e)
            }
        }
        return true
    }

    fun revokePermission(
        extensionId: String,
        permission: String,
        isPrivate: Boolean = false
    ): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        val cleanPerm = permission.trim()
        if (cleanExtId.isBlank() || cleanPerm.isBlank()) return false

        val lowerPerm = cleanPerm.lowercase()

        // Remove from API grants
        val apiMap = if (isPrivate) privateApiGrants else normalApiGrants
        val hostMap = if (isPrivate) privateHostGrants else normalHostGrants

        apiMap[cleanExtId]?.remove(lowerPerm)
        hostMap[cleanExtId]?.remove(cleanPerm)

        scope.launch {
            try {
                dao.deletePermission(cleanExtId, cleanPerm, isPrivate)
                dao.deletePermission(cleanExtId, lowerPerm, isPrivate)
            } catch (e: Exception) {
                Log.e(TAG, "Error revoking permission: $cleanPerm for $cleanExtId", e)
            }
        }
        return true
    }

    fun initializeManifestGrants(
        extensionId: String,
        requiredPermissions: List<String>,
        requiredHosts: List<String>
    ) {
        val cleanExtId = extensionId.lowercase().trim()
        if (cleanExtId.isBlank()) return

        val apiSet = normalApiGrants.computeIfAbsent(cleanExtId) { ConcurrentHashMap.newKeySet() }
        val hostSet = normalHostGrants.computeIfAbsent(cleanExtId) { ConcurrentHashMap.newKeySet() }

        val entitiesToInsert = mutableListOf<ExtensionPermissionEntity>()

        for (p in requiredPermissions) {
            val cleanP = p.lowercase().trim()
            if (cleanP.isNotBlank()) {
                apiSet.add(cleanP)
                entitiesToInsert.add(
                    ExtensionPermissionEntity(
                        extensionId = cleanExtId,
                        permission = cleanP,
                        scope = "API",
                        state = "GRANTED",
                        source = "MANIFEST",
                        isPrivateScope = false
                    )
                )
            }
        }

        for (h in requiredHosts) {
            val cleanH = h.trim()
            if (cleanH.isNotBlank()) {
                hostSet.add(cleanH)
                entitiesToInsert.add(
                    ExtensionPermissionEntity(
                        extensionId = cleanExtId,
                        permission = cleanH,
                        scope = "HOST",
                        state = "GRANTED",
                        source = "MANIFEST",
                        isPrivateScope = false
                    )
                )
            }
        }

        scope.launch {
            try {
                dao.insertAll(entitiesToInsert)
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting initial manifest grants for $cleanExtId", e)
            }
        }
    }

    fun isAllowedInPrivate(extensionId: String): Boolean {
        return allowedInPrivateExtensions.contains(extensionId.lowercase().trim())
    }

    fun setAllowedInPrivate(extensionId: String, allowed: Boolean) {
        val cleanExtId = extensionId.lowercase().trim()
        if (allowed) {
            allowedInPrivateExtensions.add(cleanExtId)
            val entity = ExtensionPermissionEntity(
                extensionId = cleanExtId,
                permission = "__PRIVATE_ALLOWED__",
                scope = "POLICY",
                state = "GRANTED",
                source = "USER_OVERRIDE",
                isPrivateScope = false
            )
            scope.launch {
                try {
                    dao.insertOrUpdate(entity)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving private policy for $cleanExtId", e)
                }
            }
        } else {
            allowedInPrivateExtensions.remove(cleanExtId)
            privateApiGrants.remove(cleanExtId)
            privateHostGrants.remove(cleanExtId)
            scope.launch {
                try {
                    dao.deletePermission(cleanExtId, "__PRIVATE_ALLOWED__", false)
                    dao.deletePermission(cleanExtId, "__PRIVATE_ALLOWED__", true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing private policy for $cleanExtId", e)
                }
            }
        }
    }

    fun removeAllForExtension(extensionId: String) {
        val cleanExtId = extensionId.lowercase().trim()
        normalApiGrants.remove(cleanExtId)
        privateApiGrants.remove(cleanExtId)
        normalHostGrants.remove(cleanExtId)
        privateHostGrants.remove(cleanExtId)
        allowedInPrivateExtensions.remove(cleanExtId)

        scope.launch {
            try {
                dao.deleteAllForExtension(cleanExtId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting permissions for $cleanExtId", e)
            }
        }
    }

    fun clearPrivateSession() {
        privateApiGrants.clear()
        privateHostGrants.clear()
        scope.launch {
            try {
                dao.clearAllPrivateScope()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing private scope from database", e)
            }
        }
    }
}
