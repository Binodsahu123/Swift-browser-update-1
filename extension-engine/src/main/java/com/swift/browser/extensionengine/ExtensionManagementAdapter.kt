package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject

class ExtensionManagementAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    private fun validate(sender: ExtensionSender, checkPermission: Boolean = true): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (checkPermission && !permissionManager.hasApiPermission(extId, ext.permissions, "management")) {
            throw SecurityException("SecurityError: Extension does not have 'management' permission")
        }
        return ext
    }

    fun getAll(sender: ExtensionSender): JSONArray {
        validate(sender, checkPermission = true)
        val list = registry.getAllActiveExtensions()
        val result = JSONArray()
        for (ext in list) {
            result.put(formatExtensionInfo(ext))
        }
        return result
    }

    fun get(sender: ExtensionSender, targetId: String): JSONObject {
        validate(sender, checkPermission = true)
        val ext = registry.getExtension(targetId) ?: throw IllegalArgumentException("Extension $targetId not found")
        return formatExtensionInfo(ext)
    }

    fun getSelf(sender: ExtensionSender): JSONObject {
        val ext = validate(sender, checkPermission = false) // getSelf doesn't strictly require management permission
        return formatExtensionInfo(ext)
    }

    fun setEnabled(sender: ExtensionSender, targetId: String, enabled: Boolean): JSONObject {
        validate(sender, checkPermission = true)
        val ext = registry.getExtension(targetId) ?: throw IllegalArgumentException("Extension $targetId not found")
        val state = if (enabled) ExtensionState.INSTALLED_ENABLED else ExtensionState.INSTALLED_DISABLED
        registry.transitionState(targetId, state)
        
        val eventName = if (enabled) "management.onEnabled" else "management.onDisabled"
        eventManager.triggerEvent(eventName, formatExtensionInfo(ext))

        return JSONObject().put("status", "success").put("enabled", enabled)
    }

    fun uninstall(sender: ExtensionSender, targetId: String, options: JSONObject? = null): JSONObject {
        validate(sender, checkPermission = true)
        val ext = registry.getExtension(targetId) ?: throw IllegalArgumentException("Extension $targetId not found")
        val info = formatExtensionInfo(ext)
        registry.unregister(targetId)

        eventManager.triggerEvent("management.onUninstalled", info)
        return JSONObject().put("status", "uninstalled").put("id", targetId)
    }

    private fun formatExtensionInfo(ext: ParsedExtension): JSONObject {
        val isEnabled = registry.isExtensionEnabled(ext.id)
        return JSONObject().apply {
            put("id", ext.id)
            put("name", ext.name)
            put("shortName", ext.name)
            put("description", ext.description)
            put("version", ext.version)
            put("enabled", isEnabled)
            put("mayDisable", true)
            put("isApp", false)
            put("type", "extension")
            put("installType", "normal")
            
            val perms = JSONArray()
            ext.permissions.forEach { perms.put(it) }
            put("permissions", perms)

            val hostPerms = JSONArray()
            ext.hostPermissions.forEach { hostPerms.put(it) }
            put("hostPermissions", hostPerms)
        }
    }
}
