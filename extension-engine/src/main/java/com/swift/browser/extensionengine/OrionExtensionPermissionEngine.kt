package com.swift.browser.extensionengine

import android.content.Context
import android.content.SharedPreferences
import android.Manifest
import com.swift.browser.permissionengine.AndroidRuntimePermissionManager

data class ExtensionPermissionInfo(
    val name: String,
    val description: String,
    val riskLevel: String, // "Low", "Medium", "High"
    val requiredAndroidPermission: String? = null
)

interface ExtensionPermissionPromptHandler {
    fun showPrompt(extId: String, extName: String, permission: String, onResult: (String) -> Unit)
}

object SwiftExtensionPermissionEngine {
    var promptHandler: ExtensionPermissionPromptHandler? = null
    private const val PREF_NAME = "swift_extension_permissions"
    
    // In-memory session store for "Allow Once" permissions (resets on app session restart)
    private val allowOncePermissions = mutableMapOf<String, MutableSet<String>>()
    private val allowOnceHosts = mutableMapOf<String, MutableSet<String>>()

    val permissionsCatalog = mapOf(
        "tabs" to ExtensionPermissionInfo("Tabs Access", "Access browser tabs, URLs, and page titles.", "Medium"),
        "activeTab" to ExtensionPermissionInfo("Active Tab", "Access the active tab context temporarily.", "Low"),
        "storage" to ExtensionPermissionInfo("Local Storage", "Store data locally in an isolated sandbox.", "Low"),
        "downloads" to ExtensionPermissionInfo("Downloads Manager", "Initiate and manage file downloads.", "Medium"),
        "notifications" to ExtensionPermissionInfo("Notifications", "Show push notifications to the user.", "Low"),
        "clipboard" to ExtensionPermissionInfo("Clipboard Access", "Read and write data to the system clipboard.", "Medium"),
        "microphone" to ExtensionPermissionInfo("Microphone Access", "Record audio from your device microphone.", "High", Manifest.permission.RECORD_AUDIO),
        "camera" to ExtensionPermissionInfo("Camera Access", "Take pictures and record videos with your camera.", "High", Manifest.permission.CAMERA),
        "location" to ExtensionPermissionInfo("Location Tracking", "Access your physical location using GPS.", "High", Manifest.permission.ACCESS_FINE_LOCATION),
        "scripting" to ExtensionPermissionInfo("Scripting Engine", "Inject custom JavaScript and CSS into websites.", "High"),
        "runtime" to ExtensionPermissionInfo("Runtime Context", "Manage background tasks and messages.", "Low"),
        "background" to ExtensionPermissionInfo("Background Agent", "Run background scripts while browser is open.", "Medium"),
        "cookies" to ExtensionPermissionInfo("Cookies Manager", "Read, modify, or block website cookies.", "High"),
        "webRequest" to ExtensionPermissionInfo("Network Interceptor", "Intercept, inspect, and modify network requests.", "High"),
        "bookmarks" to ExtensionPermissionInfo("Bookmarks", "Read, create, or edit bookmarks.", "Medium"),
        "history" to ExtensionPermissionInfo("Browsing History", "Read, clean, or search browsing history.", "High"),
        "file_access" to ExtensionPermissionInfo("File System Access", "Read or manage local workspace files.", "High"),
        "host_permissions" to ExtensionPermissionInfo("Website Hosts Control", "Interact with and run scripts on specified websites.", "High")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Returns: "ALLOW_ALWAYS", "ALLOW_ONCE", "BLOCK", "DEFAULT"
    fun getPermissionDecision(context: Context, extId: String, permission: String): String {
        // First check session-based "Allow Once"
        if (allowOncePermissions[extId]?.contains(permission) == true) {
            return "ALLOW_ONCE"
        }
        val prefs = getPrefs(context)
        val alwaysSet = prefs.getStringSet("always_$extId", emptySet()) ?: emptySet()
        if (alwaysSet.contains(permission)) {
            return "ALLOW_ALWAYS"
        }
        val blockedSet = prefs.getStringSet("blocked_$extId", emptySet()) ?: emptySet()
        if (blockedSet.contains(permission)) {
            return "BLOCK"
        }
        return "DEFAULT"
    }

    fun setPermissionDecision(context: Context, extId: String, permission: String, decision: String) {
        val prefs = getPrefs(context)
        val alwaysSet = (prefs.getStringSet("always_$extId", emptySet()) ?: emptySet()).toMutableSet()
        val blockedSet = (prefs.getStringSet("blocked_$extId", emptySet()) ?: emptySet()).toMutableSet()

        // Clear previous entries
        alwaysSet.remove(permission)
        blockedSet.remove(permission)
        allowOncePermissions[extId]?.remove(permission)

        when (decision) {
            "ALLOW_ALWAYS" -> {
                alwaysSet.add(permission)
            }
            "ALLOW_ONCE" -> {
                if (!allowOncePermissions.containsKey(extId)) {
                    allowOncePermissions[extId] = mutableSetOf()
                }
                allowOncePermissions[extId]?.add(permission)
            }
            "BLOCK" -> {
                blockedSet.add(permission)
            }
        }

        prefs.edit().apply {
            putStringSet("always_$extId", alwaysSet)
            putStringSet("blocked_$extId", blockedSet)
            apply()
        }
    }

    // Host checking helpers
    fun getHostDecision(context: Context, extId: String, hostPattern: String): String {
        if (allowOnceHosts[extId]?.contains(hostPattern) == true) {
            return "ALLOW_ONCE"
        }
        val prefs = getPrefs(context)
        val alwaysSet = prefs.getStringSet("hosts_always_$extId", emptySet()) ?: emptySet()
        if (alwaysSet.contains(hostPattern)) {
            return "ALLOW_ALWAYS"
        }
        val blockedSet = prefs.getStringSet("hosts_blocked_$extId", emptySet()) ?: emptySet()
        if (blockedSet.contains(hostPattern)) {
            return "BLOCK"
        }
        return "DEFAULT"
    }

    fun setHostDecision(context: Context, extId: String, hostPattern: String, decision: String) {
        val prefs = getPrefs(context)
        val alwaysSet = (prefs.getStringSet("hosts_always_$extId", emptySet()) ?: emptySet()).toMutableSet()
        val blockedSet = (prefs.getStringSet("hosts_blocked_$extId", emptySet()) ?: emptySet()).toMutableSet()

        alwaysSet.remove(hostPattern)
        blockedSet.remove(hostPattern)
        allowOnceHosts[extId]?.remove(hostPattern)

        when (decision) {
            "ALLOW_ALWAYS" -> {
                alwaysSet.add(hostPattern)
            }
            "ALLOW_ONCE" -> {
                if (!allowOnceHosts.containsKey(extId)) {
                    allowOnceHosts[extId] = mutableSetOf()
                }
                allowOnceHosts[extId]?.add(hostPattern)
            }
            "BLOCK" -> {
                blockedSet.add(hostPattern)
            }
        }

        prefs.edit().apply {
            putStringSet("hosts_always_$extId", alwaysSet)
            putStringSet("hosts_blocked_$extId", blockedSet)
            apply()
        }
    }

    // Reset permissions for extension
    fun resetExtensionPermissions(context: Context, extId: String) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            remove("always_$extId")
            remove("blocked_$extId")
            remove("hosts_always_$extId")
            remove("hosts_blocked_$extId")
            apply()
        }
        allowOncePermissions.remove(extId)
        allowOnceHosts.remove(extId)
    }

    // Validate if a permission is currently granted and (if high risk) if Android permission is granted
    fun isPermissionGranted(context: Context, extId: String, permission: String): Boolean {
        val decision = getPermissionDecision(context, extId, permission)
        if (decision == "BLOCK") return false
        
        // If it's a high risk permission backed by an Android permission, verify hardware validation
        val info = permissionsCatalog[permission]
        if (info != null && info.requiredAndroidPermission != null) {
            if (decision == "DEFAULT") return false // Needs explicit approval first
            return AndroidRuntimePermissionManager.hasPermission(context, info.requiredAndroidPermission)
        }
        
        // For other standard permissions, DEFAULT is allowed unless blocked or if the user wants strict opt-in.
        // Chrome defaults permissions declared in manifest as granted upon installation.
        return decision == "ALLOW_ALWAYS" || decision == "ALLOW_ONCE" || decision == "DEFAULT"
    }

    // Validate whether the extension has permission to interact with the current host URL
    fun isHostPermissionGranted(context: Context, extId: String, url: String?): Boolean {
        if (url == null || url.isBlank() || url.startsWith("about:") || url.startsWith("swift:") || url.startsWith("file://")) {
            return false
        }
        
        // Let's first search if there's any host pattern blocked.
        val prefs = getPrefs(context)
        val blockedSet = prefs.getStringSet("hosts_blocked_$extId", emptySet()) ?: emptySet()
        val alwaysSet = prefs.getStringSet("hosts_always_$extId", emptySet()) ?: emptySet()
        val onceSet = allowOnceHosts[extId] ?: emptySet()

        val allAllowed = alwaysSet + onceSet

        // Verify if the url matches any allowed pattern
        for (pattern in allAllowed) {
            if (matchesPattern(pattern, url)) {
                return true
            }
        }

        for (pattern in blockedSet) {
            if (matchesPattern(pattern, url)) {
                return false
            }
        }

        // Deny by default if no explicit decision or match
        return false
    }

    fun matchesPattern(pattern: String, url: String): Boolean {
        val p = pattern.trim()
        if (p == "<all_urls>" || p == "*://*/*" || p == "*://*" || p == "*" || p == "http://*/*" || p == "https://*/*") {
            return true
        }
        return try {
            val glob = p
            var cleanGlob = glob
            val schemeRegex = when {
                cleanGlob.startsWith("*://") -> {
                    cleanGlob = cleanGlob.substring(4)
                    "https?://"
                }
                cleanGlob.startsWith("http://") -> {
                    cleanGlob = cleanGlob.substring(7)
                    "http://"
                }
                cleanGlob.startsWith("https://") -> {
                    cleanGlob = cleanGlob.substring(8)
                    "https://"
                }
                else -> "https?://"
            }
            val parts = cleanGlob.split("/", limit = 2)
            var host = parts[0]
            val path = if (parts.size > 1) parts[1] else "*"
            
            val escapedHost = if (host.startsWith("*.")) {
                val domain = host.substring(2).replace(".", "\\.")
                "(.*\\.)?$domain"
            } else {
                host.replace(".", "\\.").replace("*", ".*")
            }
            val escapedPath = path.replace(".", "\\.").replace("*", ".*").replace("?", "\\?")
            val fullRegex = "^$schemeRegex$escapedHost/$escapedPath$"
            val compiled = java.util.regex.Pattern.compile(fullRegex, java.util.regex.Pattern.CASE_INSENSITIVE)
            compiled.matcher(url).matches()
        } catch (e: Exception) {
            false
        }
    }
}
