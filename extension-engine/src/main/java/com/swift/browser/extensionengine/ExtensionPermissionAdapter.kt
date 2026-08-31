package com.swift.browser.extensionengine

import android.content.Context
import android.util.Log
import com.swift.browser.permissionengine.ExtensionHostPatternMatcher
import com.swift.browser.permissionengine.ExtensionPermissionRepository
import com.swift.browser.permissionengine.PermissionDatabase
import com.swift.browser.permissionengine.PermissionDialogEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapter implementing the Chrome Extension Permissions API (chrome.permissions.*),
 * bridging the extension runtime with the canonical permission engine architecture.
 */
class ExtensionPermissionAdapter(
    private val context: Context,
    private val repository: ExtensionPermissionRepository? = null
) {
    private val TAG = "ExtPermissionAdapter"

    private val permissionRepo: ExtensionPermissionRepository by lazy {
        repository ?: run {
            val db = PermissionDatabase.getDatabase(context)
            ExtensionPermissionRepository(db.extensionPermissionDao())
        }
    }

    private var registry: ExtensionRegistry? = null
    private var eventManager: EventManager? = null

    fun setRegistry(extensionRegistry: ExtensionRegistry) {
        this.registry = extensionRegistry
    }

    fun setEventManager(eventMgr: EventManager) {
        this.eventManager = eventMgr
    }

    fun isAllowedInPrivate(extensionId: String): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        val ext = registry?.getExtension(cleanExtId)
        if (ext != null && !ext.allowedInPrivate) return false
        if (ext?.allowedInPrivate == true) return permissionRepo.isAllowedInPrivate(cleanExtId)
        return permissionRepo.isAllowedInPrivate(cleanExtId)
    }

    fun setAllowedInPrivate(extensionId: String, allowed: Boolean) {
        val cleanExtId = extensionId.lowercase().trim()
        permissionRepo.setAllowedInPrivate(cleanExtId, allowed)
        registry?.getExtension(cleanExtId)?.let { current ->
            val updated = current.copy(allowedInPrivate = allowed)
            registry?.register(updated)
        }
    }

    /**
     * Initializes default manifest permissions into the repository when an extension is loaded or registered.
     */
    fun onExtensionRegistered(extension: ParsedExtension) {
        val extId = extension.id.lowercase().trim()
        permissionRepo.initializeManifestGrants(
            extensionId = extId,
            requiredPermissions = extension.permissions,
            requiredHosts = extension.hostPermissions
        )
        if (extension.allowedInPrivate) {
            permissionRepo.setAllowedInPrivate(extId, true)
        }
    }

    fun onExtensionUninstalled(extensionId: String) {
        val cleanExtId = extensionId.lowercase().trim()
        permissionRepo.removeAllForExtension(cleanExtId)
    }

    /**
     * chrome.permissions.contains(permissions, callback)
     * Checks if the specified permissions and/or origins are currently granted.
     */
    fun contains(
        extensionId: String,
        permissions: List<String> = emptyList(),
        origins: List<String> = emptyList(),
        isPrivate: Boolean = false
    ): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        if (cleanExtId.isBlank()) return false
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return false

        val ext = registry?.getExtension(cleanExtId)
        if (ext != null && !ext.isEnabled) return false

        // Verify API permissions
        for (perm in permissions) {
            val cleanPerm = perm.lowercase().trim()
            if (cleanPerm.isBlank()) continue
            if (!hasApiPermission(cleanExtId, cleanPerm, isPrivate)) {
                return false
            }
        }

        // Verify Host origins
        val grantedHosts = permissionRepo.getGrantedHostPatterns(cleanExtId, isPrivate).toMutableSet()
        if (ext != null) {
            grantedHosts.addAll(ext.hostPermissions)
            grantedHosts.addAll(ext.permissions.filter { isHostPattern(it) })
        }

        for (origin in origins) {
            val cleanOrigin = origin.trim()
            if (cleanOrigin.isBlank()) continue
            val hasHost = grantedHosts.any { granted ->
                ExtensionHostPatternMatcher.isSubsetPattern(cleanOrigin, granted) ||
                        ExtensionHostPatternMatcher.matches(granted, cleanOrigin) ||
                        granted == "<all_urls>" || granted == "*://*/*"
            }
            if (!hasHost) {
                return false
            }
        }

        return true
    }

    /**
     * chrome.permissions.getAll(callback)
     * Retrieves all currently granted permissions and origins for the extension.
     */
    fun getAll(
        extensionId: String,
        isPrivate: Boolean = false
    ): ExtensionPermissionsObject {
        val cleanExtId = extensionId.lowercase().trim()
        if (cleanExtId.isBlank()) return ExtensionPermissionsObject()
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return ExtensionPermissionsObject()

        val ext = registry?.getExtension(cleanExtId)
        val allPerms = mutableSetOf<String>()
        val allOrigins = mutableSetOf<String>()

        // 1. Static manifest declared required permissions
        if (ext != null) {
            for (p in ext.permissions) {
                if (isHostPattern(p)) {
                    allOrigins.add(ExtensionHostPatternMatcher.normalizePattern(p))
                } else {
                    allPerms.add(p.lowercase().trim())
                }
            }
            for (h in ext.hostPermissions) {
                allOrigins.add(ExtensionHostPatternMatcher.normalizePattern(h))
            }
        }

        // 2. Dynamic grants from repository
        val dynamicApi = permissionRepo.getGrantedApiPermissions(cleanExtId, isPrivate)
        allPerms.addAll(dynamicApi)

        val dynamicHosts = permissionRepo.getGrantedHostPatterns(cleanExtId, isPrivate)
        for (dh in dynamicHosts) {
            allOrigins.add(ExtensionHostPatternMatcher.normalizePattern(dh))
        }

        return ExtensionPermissionsObject(
            permissions = allPerms.toList().sorted(),
            origins = allOrigins.toList().sorted()
        )
    }

    /**
     * chrome.permissions.request(permissions, callback)
     * Requests optional permissions and/or origins specified in manifest.
     */
    fun request(
        request: ExtensionPermissionRequest,
        onResult: (ExtensionPermissionResult) -> Unit
    ) {
        val cleanExtId = request.extensionId.lowercase().trim()
        val ext = registry?.getExtension(cleanExtId)
        if (ext == null) {
            onResult(ExtensionPermissionResult.Error("EXTENSION_NOT_FOUND", ExtensionPermissionErrors.EXTENSION_NOT_FOUND))
            return
        }
        if (!ext.isEnabled) {
            onResult(ExtensionPermissionResult.Error("EXTENSION_DISABLED", ExtensionPermissionErrors.EXTENSION_DISABLED))
            return
        }

        if (request.isPrivate && !isAllowedInPrivate(cleanExtId)) {
            onResult(ExtensionPermissionResult.Denied("Extension is not permitted to run in private browsing mode."))
            return
        }

        val requestedPerms = request.permissions.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val requestedOrigins = request.origins.map { it.trim() }.filter { it.isNotBlank() }

        if (requestedPerms.isEmpty() && requestedOrigins.isEmpty()) {
            onResult(ExtensionPermissionResult.Granted(emptyList(), emptyList()))
            return
        }

        // Validate that requested API permissions are declared in optional_permissions (or permissions)
        val declaredOptional = ext.optionalPermissions.map { it.lowercase().trim() }.toSet()
        val declaredPerms = ext.permissions.map { it.lowercase().trim() }.toSet()
        val declaredOptionalHosts = ext.optionalHostPermissions.map { it.trim() }.toSet()
        val declaredHosts = ext.hostPermissions.map { it.trim() }.toSet()

        for (p in requestedPerms) {
            if (!declaredOptional.contains(p) && !declaredPerms.contains(p)) {
                onResult(
                    ExtensionPermissionResult.Error(
                        "NOT_DECLARED_OPTIONAL",
                        "Permission '$p' is not declared in optional_permissions in manifest."
                    )
                )
                return
            }
        }

        // Validate that requested origins are valid match patterns and allowed by manifest
        for (origin in requestedOrigins) {
            if (!ExtensionHostPatternMatcher.isValidPattern(origin)) {
                onResult(
                    ExtensionPermissionResult.Error(
                        "INVALID_MATCH_PATTERN",
                        "Origin pattern '$origin' is not a valid match pattern."
                    )
                )
                return
            }

            val isDeclared = declaredOptionalHosts.any { ExtensionHostPatternMatcher.isSubsetPattern(origin, it) } ||
                    declaredHosts.any { ExtensionHostPatternMatcher.isSubsetPattern(origin, it) } ||
                    declaredOptional.any { it == "<all_urls>" || it == "*://*/*" || ExtensionHostPatternMatcher.isSubsetPattern(origin, it) } ||
                    declaredPerms.any { it == "<all_urls>" || it == "*://*/*" || ExtensionHostPatternMatcher.isSubsetPattern(origin, it) }

            if (!isDeclared) {
                onResult(
                    ExtensionPermissionResult.Error(
                        "NOT_DECLARED_OPTIONAL",
                        "Host pattern '$origin' is not declared in optional_host_permissions or optional_permissions in manifest."
                    )
                )
                return
            }
        }

        // If already granted, immediately return success
        if (contains(cleanExtId, requestedPerms, requestedOrigins, request.isPrivate)) {
            onResult(ExtensionPermissionResult.Granted(requestedPerms, requestedOrigins))
            return
        }

        // Dispatch UI permission prompt via PermissionDialogEngine
        PermissionDialogEngine.showExtensionPrompt(
            requestId = request.requestId,
            extensionId = cleanExtId,
            extensionName = ext.name,
            permissions = requestedPerms,
            origins = requestedOrigins,
            onResponse = { granted ->
                if (granted) {
                    for (p in requestedPerms) {
                        permissionRepo.grantPermission(
                            extensionId = cleanExtId,
                            permission = p,
                            scopeType = "API",
                            source = "OPTIONAL_RUNTIME",
                            isPrivate = request.isPrivate
                        )
                    }
                    for (o in requestedOrigins) {
                        permissionRepo.grantPermission(
                            extensionId = cleanExtId,
                            permission = o,
                            scopeType = "HOST",
                            source = "OPTIONAL_RUNTIME",
                            isPrivate = request.isPrivate
                        )
                    }

                    // Broadcast chrome.permissions.onAdded event
                    broadcastPermissionsAdded(cleanExtId, requestedPerms, requestedOrigins)

                    onResult(ExtensionPermissionResult.Granted(requestedPerms, requestedOrigins))
                } else {
                    onResult(ExtensionPermissionResult.Denied("User rejected permission request."))
                }
            }
        )
    }

    /**
     * chrome.permissions.remove(permissions, callback)
     * Revokes optional permissions or origins previously requested at runtime.
     */
    fun remove(
        extensionId: String,
        permissions: List<String> = emptyList(),
        origins: List<String> = emptyList(),
        isPrivate: Boolean = false,
        onResult: (ExtensionPermissionResult) -> Unit
    ) {
        val cleanExtId = extensionId.lowercase().trim()
        val ext = registry?.getExtension(cleanExtId)
        if (ext == null) {
            onResult(ExtensionPermissionResult.Error("EXTENSION_NOT_FOUND", ExtensionPermissionErrors.EXTENSION_NOT_FOUND))
            return
        }

        val cleanPerms = permissions.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val cleanOrigins = origins.map { it.trim() }.filter { it.isNotBlank() }

        // Security check: cannot remove required manifest permissions
        val requiredPerms = ext.permissions.map { it.lowercase().trim() }.toSet()
        val requiredHosts = ext.hostPermissions.map { it.trim() }.toSet()

        for (p in cleanPerms) {
            if (requiredPerms.contains(p)) {
                onResult(
                    ExtensionPermissionResult.Error(
                        "CANNOT_REMOVE_REQUIRED",
                        "Cannot remove required manifest permission: $p"
                    )
                )
                return
            }
        }

        for (o in cleanOrigins) {
            if (requiredHosts.contains(o)) {
                onResult(
                    ExtensionPermissionResult.Error(
                        "CANNOT_REMOVE_REQUIRED",
                        "Cannot remove required manifest host permission: $o"
                    )
                )
                return
            }
        }

        val removedPerms = mutableListOf<String>()
        val removedOrigins = mutableListOf<String>()

        for (p in cleanPerms) {
            permissionRepo.revokePermission(cleanExtId, p, isPrivate)
            removedPerms.add(p)
        }

        for (o in cleanOrigins) {
            permissionRepo.revokePermission(cleanExtId, o, isPrivate)
            removedOrigins.add(o)
        }

        if (removedPerms.isNotEmpty() || removedOrigins.isNotEmpty()) {
            broadcastPermissionsRemoved(cleanExtId, removedPerms, removedOrigins)
        }

        onResult(ExtensionPermissionResult.Granted(removedPerms, removedOrigins))
    }

    /**
     * Checks if the extension has permission to use a specific chrome.* API.
     */
    fun hasApiPermission(
        extensionId: String,
        requiredPermission: String,
        isPrivate: Boolean = false
    ): Boolean {
        val cleanExtId = extensionId.lowercase().trim()
        val cleanPerm = requiredPermission.lowercase().trim()
        if (cleanExtId.isBlank() || cleanPerm.isBlank()) return false
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return false

        val ext = registry?.getExtension(cleanExtId)
        if (ext != null) {
            if (!ext.isEnabled) return false
            if (ext.permissions.any { it.trim().equals(cleanPerm, ignoreCase = true) }) {
                return true
            }
        }

        return permissionRepo.hasApiPermission(cleanExtId, cleanPerm, isPrivate)
    }

    /**
     * Checks if the extension has host access to the specified web URL.
     */
    fun hasHostPermission(
        extensionId: String,
        url: String?,
        isPrivate: Boolean = false
    ): Boolean {
        if (url == null || url.isBlank() || url.startsWith("about:") || url.startsWith("swift:")) {
            return false
        }
        val cleanExtId = extensionId.lowercase().trim()
        if (cleanExtId.isBlank()) return false
        if (isPrivate && !isAllowedInPrivate(cleanExtId)) return false

        val ext = registry?.getExtension(cleanExtId)
        if (ext != null) {
            if (!ext.isEnabled) return false
            if (ExtensionHostPatternMatcher.matchesAny(ext.hostPermissions, url)) {
                return true
            }
            if (ExtensionHostPatternMatcher.matchesAny(ext.permissions.filter { isHostPattern(it) }, url)) {
                return true
            }
        }

        return permissionRepo.hasHostPermission(cleanExtId, url, isPrivate)
    }

    private fun isHostPattern(pattern: String): Boolean {
        val p = pattern.trim()
        return p == "<all_urls>" || p.contains("://") || p.startsWith("*:")
    }

    private fun broadcastPermissionsAdded(extensionId: String, perms: List<String>, origins: List<String>) {
        try {
            val payload = JSONObject().apply {
                put("permissions", JSONArray(perms))
                put("origins", JSONArray(origins))
            }
            eventManager?.triggerEventForExtension(extensionId, "permissions.onAdded", payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting permissions.onAdded event", e)
        }
    }

    private fun broadcastPermissionsRemoved(extensionId: String, perms: List<String>, origins: List<String>) {
        try {
            val payload = JSONObject().apply {
                put("permissions", JSONArray(perms))
                put("origins", JSONArray(origins))
            }
            eventManager?.triggerEventForExtension(extensionId, "permissions.onRemoved", payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting permissions.onRemoved event", e)
        }
    }
}
