package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.swift.browser.extensionengine.ui.ExtensionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

class ExtensionEngineApi private constructor(private val context: Context) {

    val extensionManager = ExtensionManager(context.applicationContext, null)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val okHttpClient = OkHttpClient()

    private val _uiState = MutableStateFlow(ExtensionUiState())
    val uiState: StateFlow<ExtensionUiState> = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences("swift_extension_api_prefs", Context.MODE_PRIVATE)

    init {
        // Register permission handler so prompts trigger UI state
        SwiftExtensionPermissionEngine.promptHandler = object : ExtensionPermissionPromptHandler {
            override fun showPrompt(extId: String, extName: String, permission: String, onResult: (String) -> Unit) {
                scope.launch {
                    val req = PendingExtensionPermissionRequest(extId, extName, permission) { result ->
                        onResult(result)
                        _uiState.update { it.copy(pendingPermissionRequest = null, showPermissionDialog = false) }
                    }
                    _uiState.update { it.copy(pendingPermissionRequest = req, showPermissionDialog = true) }
                }
            }
        }

        // Load initial state
        refreshExtensions()
        loadCustomScript()
    }

    fun refreshExtensions() {
        ioScope.launch {
            try {
                val dbList = extensionManager.engine.database.extensionDao().getAllExtensions()
                val parsedList = mutableListOf<ParsedExtension>()
                for (entity in dbList) {
                    try {
                        val parsed = extensionManager.engine.loader.loadFromDatabase(entity)
                        parsedList.add(parsed.copy(isEnabled = entity.enabledState))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val enabled = parsedList.filter { it.isEnabled }
                val disabled = parsedList.filter { !it.isEnabled }

                _uiState.update {
                    it.copy(
                        installedExtensions = parsedList,
                        enabledExtensions = enabled,
                        disabledExtensions = disabled
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openManagerUi() {
        refreshExtensions()
        _uiState.update { it.copy(showManagerOverlay = true) }
    }

    fun closeManagerUi() {
        _uiState.update { it.copy(showManagerOverlay = false) }
    }

    fun openActiveHub() {
        refreshExtensions()
        _uiState.update { it.copy(showActiveHubDialog = true) }
    }

    fun closeActiveHub() {
        _uiState.update { it.copy(showActiveHubDialog = false) }
    }

    fun openPopup(extensionId: String, tabId: String? = null) {
        val ext = _uiState.value.installedExtensions.find { it.id == extensionId }
            ?: extensionManager.engine.registry.getExtension(extensionId)

        if (ext != null) {
            val surface = ExtensionSurfaceResolver.resolveActionSurface(context, ext, tabId)
            when (surface.surfaceType) {
                ExtensionSurfaceType.ACTION_POPUP -> {
                    _uiState.update {
                        it.copy(
                            selectedExtension = ext,
                            activePopupExtension = ext,
                            activePopupUrl = surface.fullUrl,
                            showPopupBottomSheet = true
                        )
                    }
                }
                ExtensionSurfaceType.ACTION_ONLY -> {
                    dispatchActionClicked(ext.id, tabId)
                }
                else -> {
                    android.util.Log.d("EXT_SURFACE", "[EXT_SURFACE] No action popup or click surface for extensionId=$extensionId")
                }
            }
        }
    }

    fun openSidePanel(extensionId: String, tabId: String? = null) {
        val ext = _uiState.value.installedExtensions.find { it.id == extensionId }
            ?: extensionManager.engine.registry.getExtension(extensionId) ?: return

        val surface = ExtensionSurfaceResolver.resolveSidePanelSurface(context, ext, tabId)
        if (surface.surfaceType == ExtensionSurfaceType.SIDE_PANEL) {
            _uiState.update {
                it.copy(
                    selectedExtension = ext,
                    activePopupExtension = ext,
                    activePopupUrl = surface.fullUrl,
                    showPopupBottomSheet = true
                )
            }
        } else {
            android.util.Log.w("EXT_SURFACE", "[EXT_SURFACE] Cannot open side panel surfaceType=${surface.surfaceType} extensionId=$extensionId")
        }
    }

    fun dispatchActionClicked(extensionId: String, tabId: String? = null) {
        try {
            val ext = extensionManager.engine.registry.getExtension(extensionId)
            val tabData = org.json.JSONObject().apply {
                if (!tabId.isNullOrBlank()) {
                    val numericId = TabIdMapper.getIntId(tabId)
                    put("id", numericId)
                }
                put("active", true)
            }
            val data = org.json.JSONObject().apply {
                put("tab", tabData)
            }

            val actionName = when {
                ext?.actionSpec?.actionType == "browser_action" -> "browserAction.onClicked"
                ext?.actionSpec?.actionType == "page_action" -> "pageAction.onClicked"
                ext?.manifestVersion == 2 -> "browserAction.onClicked"
                else -> "action.onClicked"
            }

            extensionManager.engine.eventManager.triggerEventForExtension(extensionId, actionName, data)

            // Dispatch to Service Worker if active
            extensionManager.engine.swEventDispatcher.dispatchRuntimeEvent(extensionId, actionName, data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openSidePanelUrl(extensionId: String): String? {
        val ext = _uiState.value.installedExtensions.find { it.id == extensionId }
            ?: extensionManager.engine.registry.getExtension(extensionId) ?: return null
        val surface = ExtensionSurfaceResolver.resolveSidePanelSurface(context, ext)
        return if (surface.surfaceType == ExtensionSurfaceType.SIDE_PANEL) surface.fullUrl else null
    }

    fun openOptionsPageUrl(extensionId: String): String? {
        val ext = _uiState.value.installedExtensions.find { it.id == extensionId }
            ?: extensionManager.engine.registry.getExtension(extensionId) ?: return null
        val surface = ExtensionSurfaceResolver.resolveOptionsSurface(context, ext)
        return if (surface.surfaceType == ExtensionSurfaceType.OPTIONS_PAGE) surface.fullUrl else null
    }

    fun getResolvedSurfaces(extensionId: String): List<ResolvedExtensionSurface> {
        val ext = _uiState.value.installedExtensions.find { it.id == extensionId }
            ?: extensionManager.engine.registry.getExtension(extensionId) ?: return emptyList()
        return ExtensionSurfaceResolver.resolveAllSurfaces(context, ext)
    }

    fun closePopup() {
        _uiState.update {
            it.copy(
                activePopupExtension = null,
                activePopupUrl = null,
                showPopupBottomSheet = false
            )
        }
    }

    fun openStoreScreen() {
        _uiState.update { it.copy(showStoreScreen = true) }
    }

    fun closeStoreScreen() {
        _uiState.update { it.copy(showStoreScreen = false) }
    }

    fun openAnalyzerScreen() {
        _uiState.update { it.copy(showAnalyzerScreen = true) }
    }

    fun closeAnalyzerScreen() {
        _uiState.update { it.copy(showAnalyzerScreen = false) }
    }

    fun openDetailDialog(extensionId: String? = null) {
        val ext = if (extensionId != null) {
            _uiState.value.installedExtensions.find { it.id == extensionId }
        } else {
            _uiState.value.installedExtensions.firstOrNull()
        }
        _uiState.update { it.copy(selectedExtension = ext, showDetailDialog = true) }
    }

    fun closeDetailDialog() {
        _uiState.update { it.copy(showDetailDialog = false) }
    }

    fun openSettingsDialog(extensionId: String? = null) {
        _uiState.update { it.copy(selectedSettingsExtensionId = extensionId, showSettingsDialog = true) }
    }

    fun closeSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false, selectedSettingsExtensionId = null) }
    }

    fun openDeepAnalyzerDialog(extensionId: String? = null) {
        _uiState.update { it.copy(selectedAnalyzerExtensionId = extensionId, showDeepAnalyzerDialog = true) }
    }

    fun closeDeepAnalyzerDialog() {
        _uiState.update { it.copy(showDeepAnalyzerDialog = false, selectedAnalyzerExtensionId = null) }
    }

    fun openDeveloperConsole() {
        _uiState.update { it.copy(showDeveloperConsole = true) }
    }

    fun closeDeveloperConsole() {
        _uiState.update { it.copy(showDeveloperConsole = false) }
    }

    fun openZipInstaller() {
        _uiState.update { it.copy(showZipInstaller = true) }
    }

    fun closeZipInstaller() {
        _uiState.update { it.copy(showZipInstaller = false) }
    }

    fun setEnabled(extensionId: String, enabled: Boolean) {
        ioScope.launch {
            try {
                extensionManager.toggleExtension(extensionId, enabled)
                
                // Dark Reader special case handling (Rule 11)
                if (extensionId.contains("dark_reader", ignoreCase = true)) {
                    applyDarkReaderSpecialHandling(enabled)
                }

                refreshExtensions()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to toggle extension: ${e.localizedMessage}") }
            }
        }
    }

    private fun applyDarkReaderSpecialHandling(enabled: Boolean) {
        // Persist dark reader filter flag for content scripts
        prefs.edit().putBoolean("dark_reader_active", enabled).apply()
    }

    fun installFromZip(uri: Uri) {
        _uiState.update { it.copy(isInstalling = true, installProgressMessage = "Validating and extracting extension ZIP...") }
        ioScope.launch {
            try {
                val parsed = extensionManager.installExtension(uri)
                refreshExtensions()
                _uiState.update {
                    it.copy(
                        isInstalling = false,
                        installProgressMessage = null,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInstalling = false,
                        installProgressMessage = null,
                        errorMessage = "ZIP Install Error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun uninstallExtension(extensionId: String) {
        ioScope.launch {
            try {
                extensionManager.uninstallExtension(extensionId)
                SwiftExtensionPermissionEngine.resetExtensionPermissions(context, extensionId)
                refreshExtensions()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Uninstall Error: ${e.localizedMessage}") }
            }
        }
    }

    fun exportExtension(extensionId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        ExtensionWebStoreManager.exportExtension(
            scope = ioScope,
            context = context,
            extensionId = extensionId,
            extensionManager = extensionManager
        ) { success, msg ->
            if (onResult != null) {
                onResult(success, msg)
            } else if (!success) {
                _uiState.update { it.copy(errorMessage = msg) }
            }
        }
    }

    fun shareExtension(extensionId: String) {
        ExtensionWebStoreManager.shareExtension(
            scope = ioScope,
            context = context,
            extensionId = extensionId,
            extensionManager = extensionManager
        )
    }

    fun searchStore(query: String, apiKey: String = "") {
        _uiState.update { it.copy(storeQuery = query, isStoreLoading = true) }
        ExtensionWebStoreManager.searchChromeWebStore(
            scope = ioScope,
            query = query,
            apiKey = apiKey,
            okHttpClient = okHttpClient
        ) { results ->
            _uiState.update { it.copy(storeResults = results, isStoreLoading = false) }
        }
    }

    fun installFromStore(extensionId: String) {
        _uiState.update { it.copy(isInstalling = true, installProgressMessage = "Downloading from Chrome Web Store...") }
        ExtensionWebStoreManager.downloadChromeExtension(
            scope = ioScope,
            context = context,
            extensionId = extensionId,
            okHttpClient = okHttpClient,
            extensionManager = extensionManager,
            onInstalled = { refreshExtensions() },
            onResult = { success, msg ->
                _uiState.update {
                    it.copy(
                        isInstalling = false,
                        installProgressMessage = null,
                        errorMessage = if (success) null else msg
                    )
                }
            }
        )
    }

    fun getLocalFallbackExtensions(): List<ExtensionMeta> {
        return ExtensionWebStoreManager.getLocalFallbackExtensions()
    }

    fun loadCustomScript() {
        val script = prefs.getString("custom_extension_script", "") ?: ""
        val enabled = prefs.getBoolean("custom_extension_script_enabled", false)
        _uiState.update { it.copy(customScript = script, isCustomScriptEnabled = enabled) }
    }

    fun setCustomExtensionScript(script: String, enabled: Boolean) {
        prefs.edit()
            .putString("custom_extension_script", script)
            .putBoolean("custom_extension_script_enabled", enabled)
            .apply()
        _uiState.update { it.copy(customScript = script, isCustomScriptEnabled = enabled) }
    }

    fun getCustomExtensionScript(): String {
        return if (_uiState.value.isCustomScriptEnabled) _uiState.value.customScript else ""
    }

    fun checkChromeWebStorePage(url: String): String? {
        if (url.isBlank()) return null
        return when {
            url.contains("chromewebstore.google.com/detail/") -> {
                url.substringAfter("detail/").substringAfterLast("/").take(32)
            }
            url.contains("chrome.google.com/webstore/detail/") -> {
                url.substringAfter("detail/").substringAfterLast("/").take(32)
            }
            else -> null
        }
    }

    fun setupWebView(webView: WebView, tabId: String? = null) {
        extensionManager.setupWebView(webView, tabId)
    }

    fun notifyTabUpdated(tabId: String, url: String, status: String = "complete") {
        try {
            val intTabId = TabIdMapper.getIntId(tabId)
            val data = org.json.JSONObject().apply {
                put("tabId", intTabId)
                put("changeInfo", org.json.JSONObject().apply {
                    put("status", status)
                    put("url", url)
                })
                put("tab", org.json.JSONObject().apply {
                    put("id", intTabId)
                    put("url", url)
                    put("status", status)
                })
            }
            extensionManager.engine.eventManager.triggerEvent("tabs.onUpdated", data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyTabActivated(tabId: String) {
        try {
            val intTabId = TabIdMapper.getIntId(tabId)
            val data = org.json.JSONObject().apply {
                put("activeInfo", org.json.JSONObject().apply {
                    put("tabId", intTabId)
                })
            }
            extensionManager.engine.eventManager.triggerEvent("tabs.onActivated", data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyTabCreated(tabId: String, url: String = "") {
        try {
            val intTabId = TabIdMapper.getIntId(tabId)
            val data = org.json.JSONObject().apply {
                put("tab", org.json.JSONObject().apply {
                    put("id", intTabId)
                    put("url", url)
                    put("active", true)
                })
            }
            extensionManager.engine.eventManager.triggerEvent("tabs.onCreated", data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyTabRemoved(tabId: String) {
        try {
            val intTabId = TabIdMapper.getIntId(tabId)
            val data = org.json.JSONObject().apply {
                put("tabId", intTabId)
                put("removeInfo", org.json.JSONObject().apply {
                    put("isWindowClosing", false)
                })
            }
            extensionManager.engine.eventManager.triggerEvent("tabs.onRemoved", data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onPageStarted(webView: WebView, url: String) {
        extensionManager.engine.contentScriptManager.onPageStarted(webView, url)
    }

    fun injectContentScripts(
        webView: WebView,
        url: String,
        runAt: String? = null,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ) {
        extensionManager.injectContentScripts(webView, url, runAt, isPrivate, privateSessionId)
        // Inject custom user script if enabled
        val customScript = getCustomExtensionScript()
        if (customScript.isNotBlank()) {
            webView.post {
                webView.evaluateJavascript("(function(){ $customScript })();", null)
            }
        }
    }

    fun getPermissionDecision(extId: String, permission: String): String {
        return SwiftExtensionPermissionEngine.getPermissionDecision(context, extId, permission)
    }

    fun setPermissionDecision(extId: String, permission: String, decision: String) {
        SwiftExtensionPermissionEngine.setPermissionDecision(context, extId, permission, decision)
        refreshExtensions()
    }

    fun getHostDecision(extId: String, hostPattern: String): String {
        return SwiftExtensionPermissionEngine.getHostDecision(context, extId, hostPattern)
    }

    fun setHostDecision(extId: String, hostPattern: String, decision: String) {
        SwiftExtensionPermissionEngine.setHostDecision(context, extId, hostPattern, decision)
        refreshExtensions()
    }

    fun hasAndroidPermission(permission: String): Boolean {
        return com.swift.browser.permissionengine.AndroidRuntimePermissionManager.hasPermission(context, permission)
    }

    fun getPermissionInfo(permission: String): ExtensionPermissionInfo? {
        return SwiftExtensionPermissionEngine.permissionsCatalog[permission]
    }

    fun confirmInstall(extensionId: String, selectedPermissions: List<String> = emptyList(), selectedHostPermissions: List<String> = emptyList()) {
        ioScope.launch {
            try {
                selectedPermissions.forEach { perm ->
                    SwiftExtensionPermissionEngine.setPermissionDecision(context, extensionId, perm, "ALLOW_ALWAYS")
                }
                selectedHostPermissions.forEach { host ->
                    SwiftExtensionPermissionEngine.setHostDecision(context, extensionId, host, "ALLOW_ALWAYS")
                }
                extensionManager.toggleExtension(extensionId, true)
                refreshExtensions()
                _uiState.update { it.copy(showDetailDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Install Approval Error: ${e.localizedMessage}") }
            }
        }
    }

    fun dismissError() {

        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getEnabledExtensions(): List<ParsedExtension> {
        return _uiState.value.enabledExtensions
    }

    companion object {
        @Volatile
        private var instance: ExtensionEngineApi? = null

        fun getInstance(context: Context): ExtensionEngineApi {
            return instance ?: synchronized(this) {
                instance ?: ExtensionEngineApi(context.applicationContext).also { instance = it }
            }
        }
    }
}
