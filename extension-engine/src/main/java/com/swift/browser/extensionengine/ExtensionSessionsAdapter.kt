package com.swift.browser.extensionengine

import com.swift.browser.tabengine.api.TabEngineApi
import org.json.JSONArray
import org.json.JSONObject

class ExtensionSessionsAdapter(
    private val tabEngine: TabEngineApi,
    private val tabsAdapter: ExtensionTabsAdapter,
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager
) {

    fun restore(sender: ExtensionSender, sessionId: String?): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val restoredGroups = tabEngine.restoreSession()
        val restoredTab = restoredGroups.firstOrNull()?.tabs?.firstOrNull()

        return JSONObject().apply {
            if (restoredTab != null) {
                val isActive = tabEngine.getActiveTab()?.id == restoredTab.id
                put("tab", tabsAdapter.formatTabObject(restoredTab, sender.extensionId, isActive))
                put("lastModified", System.currentTimeMillis() / 1000)
            } else {
                put("tab", JSONObject.NULL)
            }
        }
    }

    fun getDevices(sender: ExtensionSender): JSONArray {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        // Return current device session info
        val currentDevice = JSONObject().apply {
            put("deviceName", "Orion Browser Device")
            put("sessions", JSONArray())
        }
        return JSONArray().apply { put(currentDevice) }
    }

    fun getRecentlyClosed(sender: ExtensionSender, filter: JSONObject? = null): JSONArray {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }
        return JSONArray()
    }
}
