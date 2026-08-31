package com.swift.browser.extensionengine

import com.swift.browser.tabengine.api.TabEngineApi
import org.json.JSONArray
import org.json.JSONObject

class ExtensionWindowsAdapter(
    private val tabEngine: TabEngineApi,
    private val tabsAdapter: ExtensionTabsAdapter,
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager
) {

    fun getWindow(sender: ExtensionSender, windowId: Int, populate: Boolean): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val isPrivateWindow = windowId == 2
        if (isPrivateWindow && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
            throw SecurityException("Access to private window denied")
        }

        return formatWindowObject(windowId = if (isPrivateWindow) 2 else 1, sender = sender, populate = populate)
    }

    fun getCurrentWindow(sender: ExtensionSender, populate: Boolean): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val activeTab = tabEngine.getActiveTab()
        val windowId = if (activeTab?.isPrivate == true || activeTab?.isIncognito == true) 2 else 1
        return formatWindowObject(windowId = windowId, sender = sender, populate = populate)
    }

    fun getLastFocusedWindow(sender: ExtensionSender, populate: Boolean): JSONObject {
        return getCurrentWindow(sender, populate)
    }

    fun getAllWindows(sender: ExtensionSender, populate: Boolean): JSONArray {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val array = JSONArray()
        array.put(formatWindowObject(windowId = 1, sender = sender, populate = populate))

        if (permissionManager.isAllowedInPrivate(sender.extensionId) && tabEngine.getPrivateTabs().isNotEmpty()) {
            array.put(formatWindowObject(windowId = 2, sender = sender, populate = populate))
        }

        return array
    }

    fun updateWindow(sender: ExtensionSender, windowId: Int, updateProperties: JSONObject): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val isFocused = updateProperties.optBoolean("focused", true)
        val state = updateProperties.optString("state", "normal")

        return formatWindowObject(windowId = windowId, sender = sender, populate = false).apply {
            put("focused", isFocused)
            put("state", state)
        }
    }

    fun removeWindow(sender: ExtensionSender, windowId: Int) {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val tabsToClose = if (windowId == 2) {
            tabEngine.getPrivateTabs()
        } else {
            tabEngine.getNormalTabs()
        }

        tabsToClose.forEach { tab ->
            tabEngine.closeTab(tab.id)
        }
    }

    private fun formatWindowObject(windowId: Int, sender: ExtensionSender, populate: Boolean): JSONObject {
        val isPrivateWindow = windowId == 2
        val activeTabId = tabEngine.getActiveTab()?.id

        val windowObj = JSONObject().apply {
            put("id", windowId)
            put("focused", true)
            put("top", 0)
            put("left", 0)
            put("width", 1080)
            put("height", 1920)
            put("incognito", isPrivateWindow)
            put("type", "normal")
            put("state", "normal")
            put("alwaysOnTop", false)
        }

        if (populate) {
            val tabs = if (isPrivateWindow) tabEngine.getPrivateTabs() else tabEngine.getNormalTabs()
            val tabsArray = JSONArray()
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                tabsArray.put(tabsAdapter.formatTabObject(tab, sender.extensionId, isActive))
            }
            windowObj.put("tabs", tabsArray)
        }

        return windowObj
    }
}
