package com.swift.browser.extensionengine

class ExtensionCapabilityMap(private val extension: ParsedExtension) {

    fun hasPermission(permission: String): Boolean {
        if (permission.isBlank()) return false
        val target = permission.lowercase().trim()
        return extension.permissions.any { it.lowercase().trim() == target } ||
                extension.optionalPermissions.any { it.lowercase().trim() == target }
    }

    fun hasHostPermission(origin: String): Boolean {
        if (origin.isBlank()) return false
        val allHosts = extension.hostPermissions + extension.permissions.filter {
            it.contains("://") || it == "<all_urls>"
        }
        return ExtensionMatchPattern.matchesAny(origin, allHosts)
    }

    fun hasContentScriptForUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return extension.contentScripts.any { cs -> cs.matchesUrl(url) }
    }

    fun hasBackgroundServiceWorker(): Boolean {
        return extension.isServiceWorker || extension.backgroundSpec.serviceWorker.isNotBlank()
    }

    fun hasBackgroundPage(): Boolean {
        return !extension.isServiceWorker && (extension.backgroundSpec.page.isNotBlank() || extension.backgroundSpec.scripts.isNotEmpty())
    }
}
