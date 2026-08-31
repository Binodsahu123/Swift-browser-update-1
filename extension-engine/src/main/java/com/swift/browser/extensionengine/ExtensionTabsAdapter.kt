package com.swift.browser.extensionengine

import com.swift.browser.tabengine.api.TabEngineApi
import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class ExtensionTabsAdapter(
    val tabEngine: TabEngineApi,
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager,
    private val messageBus: MessageBus = MessageBus(),
    private val portManager: PortManager = PortManager(messageBus)
) {
    fun createTab(sender: ExtensionSender, createProperties: JSONObject): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val url = createProperties.optString("url", "swift://newtab")
        val active = createProperties.optBoolean("active", true)
        val groupIdStr = if (createProperties.has("groupId")) {
            createProperties.get("groupId").toString()
        } else null

        val reqWindowId = if (createProperties.has("windowId")) createProperties.getInt("windowId") else null
        val isPrivate = if (reqWindowId != null) {
            reqWindowId == 2
        } else {
            sender.isPrivate
        }

        if (isPrivate && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
            throw SecurityException("Access to private window denied")
        }

        val newTabModel = if (isPrivate) {
            val sessionId = sender.privateSessionId ?: "default_private_session"
            tabEngine.createPrivateTab(
                sessionId = sessionId,
                url = url,
                title = "Private Tab",
                groupId = groupIdStr
            )
        } else {
            tabEngine.createTab(
                url = url,
                title = "New Tab",
                isIncognito = false,
                groupId = groupIdStr
            )
        }

        if (active) {
            tabEngine.switchTab(newTabModel.id)
        }

        return formatTabObject(newTabModel, sender.extensionId, active)
    }

    fun getTab(sender: ExtensionSender, tabIdInput: Any?): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val uuid = resolveTabUuid(tabIdInput, sender)
        val tab = tabEngine.getTab(uuid) ?: throw IllegalArgumentException("Tab not found: $tabIdInput")

        if ((tab.isPrivate || tab.isIncognito) && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
            throw SecurityException("Access to private tab denied")
        }

        val isActive = tabEngine.getActiveTab()?.id == tab.id
        return formatTabObject(tab, sender.extensionId, isActive)
    }

    fun queryTabs(sender: ExtensionSender, queryInfo: JSONObject): JSONArray {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val hasPrivateAccess = permissionManager.isAllowedInPrivate(sender.extensionId)

        // Determine which windows to query
        val targetWindowId = when {
            queryInfo.has("windowId") -> queryInfo.getInt("windowId")
            queryInfo.optBoolean("currentWindow", false) || queryInfo.optBoolean("lastFocusedWindow", false) -> {
                val activeTab = tabEngine.getActiveTab()
                if (activeTab?.isPrivate == true) 2 else 1
            }
            else -> null
        }

        val tabsToQuery = mutableListOf<TabModel>()
        if (targetWindowId != null) {
            if (targetWindowId == 2) {
                if (hasPrivateAccess) {
                    val pTabs = if (!sender.privateSessionId.isNullOrBlank()) {
                        tabEngine.getPrivateTabs(sender.privateSessionId)
                    } else {
                        tabEngine.getPrivateTabs()
                    }
                    tabsToQuery.addAll(pTabs)
                }
            } else if (targetWindowId == 1) {
                tabsToQuery.addAll(tabEngine.getNormalTabs())
            }
        } else {
            // Include normal tabs
            tabsToQuery.addAll(tabEngine.getNormalTabs())
            // Include private tabs if allowed
            if (hasPrivateAccess) {
                val pTabs = if (!sender.privateSessionId.isNullOrBlank()) {
                    tabEngine.getPrivateTabs(sender.privateSessionId)
                } else {
                    tabEngine.getPrivateTabs()
                }
                tabsToQuery.addAll(pTabs)
            }
        }

        val activeTabId = tabEngine.getActiveTab()?.id

        // Apply filters
        val activeFilter = if (queryInfo.has("active")) queryInfo.getBoolean("active") else null
        val urlFilter = if (queryInfo.has("url")) queryInfo.getString("url") else null
        val titleFilter = if (queryInfo.has("title")) queryInfo.getString("title") else null
        val statusFilter = if (queryInfo.has("status")) queryInfo.getString("status") else null
        val groupIdFilter = if (queryInfo.has("groupId")) queryInfo.get("groupId") else null

        val filtered = tabsToQuery.filter { tab ->
            val isActive = tab.id == activeTabId
            if (activeFilter != null && isActive != activeFilter) return@filter false
            if (urlFilter != null && !matchesUrlPattern(tab.url, urlFilter)) return@filter false
            if (titleFilter != null && !tab.title.contains(titleFilter, ignoreCase = true)) return@filter false
            if (statusFilter != null) {
                val currentStatus = "complete"
                if (!currentStatus.equals(statusFilter, ignoreCase = true)) return@filter false
            }
            if (groupIdFilter != null) {
                val tabGroupIntId = tab.groupId?.hashCode() ?: -1
                if (groupIdFilter is Number) {
                    if (tabGroupIntId != groupIdFilter.toInt()) return@filter false
                } else if (groupIdFilter.toString() != tab.groupId) {
                    return@filter false
                }
            }
            true
        }

        val jsonArray = JSONArray()
        filtered.forEachIndexed { idx, tab ->
            val isActive = tab.id == activeTabId
            val tabObj = formatTabObject(tab, sender.extensionId, isActive)
            tabObj.put("index", idx)
            jsonArray.put(tabObj)
        }
        return jsonArray
    }

    fun updateTab(sender: ExtensionSender, tabIdInput: Any?, updateProperties: JSONObject): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val uuid = resolveTabUuid(tabIdInput, sender)
        val tab = tabEngine.getTab(uuid) ?: throw IllegalArgumentException("Tab not found: $tabIdInput")

        if ((tab.isPrivate || tab.isIncognito) && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
            throw SecurityException("Access to private tab denied")
        }

        if (updateProperties.has("url")) {
            val newUrl = updateProperties.getString("url")
            tabEngine.updateTab(uuid) { it.copy(url = newUrl) }
        }

        if (updateProperties.optBoolean("active", false) || updateProperties.optBoolean("selected", false)) {
            tabEngine.switchTab(uuid)
        }

        val updatedTab = tabEngine.getTab(uuid) ?: tab
        val isActive = tabEngine.getActiveTab()?.id == updatedTab.id
        return formatTabObject(updatedTab, sender.extensionId, isActive)
    }

    fun removeTabs(sender: ExtensionSender, tabIdInputs: List<Any>): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        for (input in tabIdInputs) {
            val uuid = resolveTabUuid(input, sender)
            tabEngine.closeTab(uuid)
            portManager.cleanupForTab(uuid)
        }
        return JSONObject().apply { put("success", true) }
    }

    fun reloadTab(sender: ExtensionSender, tabIdInput: Any?) {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }
        val uuid = resolveTabUuid(tabIdInput, sender)
        val webView = tabEngine.getWebView(uuid)
        webView?.post { webView.reload() }
    }

    fun goBack(sender: ExtensionSender, tabIdInput: Any?): Boolean {
        val uuid = resolveTabUuid(tabIdInput, sender)
        val webView = tabEngine.getWebView(uuid) ?: return false
        webView.post {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }
        return true
    }

    fun goForward(sender: ExtensionSender, tabIdInput: Any?): Boolean {
        val uuid = resolveTabUuid(tabIdInput, sender)
        val webView = tabEngine.getWebView(uuid) ?: return false
        webView.post {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }
        return true
    }

    fun sendMessageToTab(
        sender: ExtensionSender,
        targetTabIdInput: Any?,
        message: JSONObject,
        callbackId: String?
    ) {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val uuid = resolveTabUuid(targetTabIdInput, sender)
        val targetTab = tabEngine.getTab(uuid) ?: throw IllegalArgumentException("Target tab not found: $targetTabIdInput")

        messageBus.broadcastMessage(
            sender = sender.copy(contextType = ExtensionContextType.BACKGROUND, tabId = targetTab.id),
            message = message,
            callbackId = callbackId,
            targetTabId = uuid
        )
    }

    fun connectToTab(
        sender: ExtensionSender,
        targetTabIdInput: Any?,
        connectInfo: JSONObject?
    ): PortConnection {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val uuid = resolveTabUuid(targetTabIdInput, sender)
        val channelId = "port_tab_${uuid}_${System.currentTimeMillis()}"
        val name = connectInfo?.optString("name", "") ?: ""

        portManager.connect(
            extensionId = sender.extensionId,
            channelId = channelId,
            portName = name,
            senderId = sender.extensionId,
            sender = sender,
            tabId = uuid
        )

        return portManager.registry.get(channelId)!!
    }

    private fun resolveTabUuid(input: Any?, sender: ExtensionSender): String {
        if (input == null || input == "" || input == "null") {
            val active = tabEngine.getActiveTab()
            if (active != null) {
                if (active.isPrivate && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
                    return tabEngine.getNormalTabs().firstOrNull()?.id ?: active.id
                }
                return active.id
            }
            return ""
        }
        return when (input) {
            is Int -> TabIdMapper.getUuid(input) ?: input.toString()
            is Number -> TabIdMapper.getUuid(input.toInt()) ?: input.toString()
            is String -> {
                val parsedInt = input.toIntOrNull()
                if (parsedInt != null) {
                    TabIdMapper.getUuid(parsedInt) ?: input
                } else {
                    TabIdMapper.getUuidFromString(input)
                }
            }
            else -> input.toString()
        }
    }

    private fun matchesUrlPattern(url: String, pattern: String): Boolean {
        if (pattern.isBlank()) return true
        if (pattern == "<all_urls>") return true
        return try {
            if (pattern.contains("*")) {
                PermissionManager.matchHostPattern(url, pattern)
            } else {
                url.contains(pattern, ignoreCase = true)
            }
        } catch (e: Exception) {
            url.contains(pattern, ignoreCase = true)
        }
    }

    fun formatTabObject(tab: TabModel, extensionId: String, isActive: Boolean): JSONObject {
        val intId = TabIdMapper.getIntId(tab.id)
        val ext = registry.getExtension(extensionId)
        val hasPermission = ext != null && (
                permissionManager.hasApiPermission(extensionId, ext.permissions, "tabs") ||
                permissionManager.hasApiPermission(extensionId, ext.permissions, "activeTab")
        )

        return JSONObject().apply {
            put("id", intId)
            put("index", 0)
            put("windowId", if (tab.isPrivate || tab.isIncognito) 2 else 1)
            put("active", isActive)
            put("selected", isActive)
            put("highlighted", isActive)
            put("pinned", false)
            put("status", "complete")
            put("discarded", tab.freezeState != 0)
            put("autoDiscardable", true)
            put("incognito", tab.isIncognito || tab.isPrivate)
            if (tab.groupId != null) {
                put("groupId", tab.groupId.hashCode())
            } else {
                put("groupId", -1)
            }
            if (hasPermission || isActive) {
                val canShowSensitive = !tab.isPrivate || permissionManager.isAllowedInPrivate(extensionId)
                if (canShowSensitive) {
                    put("url", tab.url)
                    put("title", tab.title)
                    if (tab.faviconUrl != null) put("favIconUrl", tab.faviconUrl)
                }
            }
        }
    }
}
