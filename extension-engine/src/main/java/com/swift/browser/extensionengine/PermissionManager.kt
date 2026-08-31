package com.swift.browser.extensionengine

import android.content.Context
import com.swift.browser.permissionengine.ExtensionHostPatternMatcher
import java.util.regex.Pattern

/**
 * Compatibility facade for extension permission queries, backed by ExtensionHostPatternMatcher
 * and ExtensionPermissionAdapter.
 */
class PermissionManager(val context: Context) {

    private var registry: ExtensionRegistry? = null
    private val adapter: ExtensionPermissionAdapter by lazy {
        ExtensionPermissionAdapter(context)
    }

    fun setRegistry(extensionRegistry: ExtensionRegistry) {
        this.registry = extensionRegistry
        adapter.setRegistry(extensionRegistry)
    }

    fun isAllowedInPrivate(extensionId: String): Boolean {
        return adapter.isAllowedInPrivate(extensionId)
    }

    @Deprecated("Use isAllowedInPrivate instead", ReplaceWith("isAllowedInPrivate(extensionId)"))
    fun isAllowedInIncognito(extensionId: String): Boolean = isAllowedInPrivate(extensionId)

    fun setAllowedInPrivate(extensionId: String, allowed: Boolean) {
        adapter.setAllowedInPrivate(extensionId, allowed)
    }

    // Inspects whether the given active extension has authority over the specific site URL.
    fun hasHostPermission(extensionId: String, hostPermissions: List<String>, permissions: List<String>, url: String?): Boolean {
        if (url == null || url.isBlank() || url.startsWith("about:") || url.startsWith("swift:")) {
            return false
        }

        // 1. Check direct adapter
        if (adapter.hasHostPermission(extensionId, url)) {
            return true
        }

        // 2. Fallback check against provided manifest host lists
        val combinedHosts = hostPermissions + permissions.filter { isUrlPattern(it) }
        return ExtensionHostPatternMatcher.matchesAny(combinedHosts, url)
    }

    // Checks if the extension was granted standard API permission string (e.g., "storage", "tabs").
    fun hasApiPermission(extensionId: String, permissions: List<String>, requiredPermission: String): Boolean {
        if (adapter.hasApiPermission(extensionId, requiredPermission)) {
            return true
        }
        return permissions.any { it.trim().equals(requiredPermission.trim(), ignoreCase = true) }
    }

    private fun isUrlPattern(pattern: String): Boolean {
        return pattern.contains("://") || pattern == "<all_urls>" || pattern.startsWith("*:")
    }

    companion object {
        fun matchHostPattern(urlStr: String, pattern: String): Boolean {
            return ExtensionHostPatternMatcher.matches(pattern, urlStr)
        }
    }
}
