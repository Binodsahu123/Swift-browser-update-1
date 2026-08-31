package com.swift.browser.extensionengine

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.swift.browser.tabengine.api.TabEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

interface BrowserDelegate {
    fun queryTabs(queryInfo: JSONObject): JSONArray
    fun createTab(url: String, active: Boolean)
    fun removeTab(tabId: String)
    fun reloadTab(tabId: String)
    fun updateTab(tabId: String, url: String)
    fun showNotification(title: String, message: String)
    fun downloadFile(url: String, filename: String?)
    fun getActiveTabId(): String?
    fun executeScriptOnTab(tabId: String, code: String, callback: (String?) -> Unit)
    fun checkExtensionPermission(extensionId: String, permission: String, callback: (Boolean) -> Unit)
}

class RuntimeBridge(
    val context: Context,
    val webView: WebView?,
    val storageManager: StorageManager,
    val messageBus: MessageBus,
    val delegate: BrowserDelegate?,
    val eventManager: EventManager,
    val tabId: String? = null,
    val portManager: PortManager? = null,
    val tabBridge: TabBridge? = null,
    val registry: ExtensionRegistry,
    val permissionManager: PermissionManager,
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null,
    val permissionsAdapter: ExtensionPermissionAdapter = ExtensionPermissionAdapter(context),
    val dnrAdapter: ExtensionDnrAdapter = ExtensionDnrAdapter(permissionManager, registry),
    val webRequestAdapter: ExtensionWebRequestAdapter = ExtensionWebRequestAdapter(permissionManager, registry, eventManager, dnrAdapter),
    val scriptingAdapter: ExtensionScriptingAdapter = ExtensionScriptingAdapter(permissionAdapter = permissionsAdapter, registry = registry, contentScriptManager = ContentScriptManager(context, permissionManager, ScriptInjector(), CssInjector(), registry)),
    val actionAdapter: ExtensionActionAdapter = ExtensionActionAdapter(permissionManager, registry, eventManager),
    val contextMenusAdapter: ExtensionContextMenusAdapter = ExtensionContextMenusAdapter(permissionManager, registry, eventManager),
    val commandsAdapter: ExtensionCommandsAdapter = ExtensionCommandsAdapter(permissionManager, registry, eventManager),
    val omniboxAdapter: ExtensionOmniboxAdapter = ExtensionOmniboxAdapter(permissionManager, registry, eventManager),
    val sidePanelAdapter: ExtensionSidePanelAdapter = ExtensionSidePanelAdapter(permissionManager, registry),
    val managementAdapter: ExtensionManagementAdapter = ExtensionManagementAdapter(permissionManager, registry, eventManager),
    val topSitesAdapter: ExtensionTopSitesAdapter = ExtensionTopSitesAdapter(permissionManager, registry),
    val idleAdapter: ExtensionIdleAdapter = ExtensionIdleAdapter(permissionManager, registry),
    val ttsAdapter: ExtensionTtsAdapter = ExtensionTtsAdapter(permissionManager, registry),
    val searchAdapter: ExtensionSearchAdapter = ExtensionSearchAdapter(permissionManager, registry),
    val alarmsAdapter: ExtensionAlarmsAdapter = ExtensionAlarmsAdapter(permissionManager, registry, eventManager),
    val systemAdapter: ExtensionSystemAdapter = ExtensionSystemAdapter(permissionManager, registry),
    val notificationsAdapter: ExtensionNotificationsAdapter = ExtensionNotificationsAdapter(context, permissionManager, registry, eventManager),
    val tabsAdapter: ExtensionTabsAdapter = ExtensionTabsAdapter(tabEngine = TabEngineProvider.getEngine(context, CoroutineScope(Dispatchers.Main)), registry = registry, permissionManager = permissionManager, messageBus = messageBus, portManager = portManager ?: PortManager(messageBus)),
    val windowsAdapter: ExtensionWindowsAdapter = ExtensionWindowsAdapter(TabEngineProvider.getEngine(context, CoroutineScope(Dispatchers.Main)), tabsAdapter, registry, permissionManager),
    val tabGroupsAdapter: ExtensionTabGroupsAdapter = ExtensionTabGroupsAdapter(TabEngineProvider.getEngine(context, CoroutineScope(Dispatchers.Main)), registry, permissionManager),
    val sessionsAdapter: ExtensionSessionsAdapter = ExtensionSessionsAdapter(TabEngineProvider.getEngine(context, CoroutineScope(Dispatchers.Main)), tabsAdapter, registry, permissionManager),
    val cookieAdapter: ExtensionCookieAdapter = ExtensionCookieAdapter(context, permissionsAdapter, registry, eventManager),
    val bookmarksAdapter: ExtensionBookmarksAdapter = ExtensionBookmarksAdapter(context, permissionManager, registry, eventManager),
    val historyAdapter: ExtensionHistoryAdapter = ExtensionHistoryAdapter(permissionManager, registry, eventManager),
    val downloadsAdapter: ExtensionDownloadsAdapter = ExtensionDownloadsAdapter(context, permissionManager, registry, eventManager),
    val serviceWorkerEventDispatcher: ServiceWorkerEventDispatcher? = null,
    val messageRouter: ExtensionMessageRouter? = null
) : MessageListener, PortConnectionListener {

    private val mainScope = CoroutineScope(Dispatchers.Main)

    val jsEventRegistry = ExtensionJsEventRegistry(eventManager, webRequestAdapter)

    val jsRouter = ExtensionJsBridgeRouter(
        registry = registry,
        permissionAdapter = permissionsAdapter,
        storageManager = storageManager,
        messageBus = messageBus,
        eventManager = eventManager,
        tabsAdapter = tabsAdapter,
        windowsAdapter = windowsAdapter,
        tabGroupsAdapter = tabGroupsAdapter,
        sessionsAdapter = sessionsAdapter,
        cookieAdapter = cookieAdapter,
        bookmarksAdapter = bookmarksAdapter,
        historyAdapter = historyAdapter,
        downloadsAdapter = downloadsAdapter,
        dnrAdapter = dnrAdapter,
        webRequestAdapter = webRequestAdapter,
        scriptingAdapter = scriptingAdapter,
        actionAdapter = actionAdapter,
        contextMenusAdapter = contextMenusAdapter,
        commandsAdapter = commandsAdapter,
        omniboxAdapter = omniboxAdapter,
        sidePanelAdapter = sidePanelAdapter,
        managementAdapter = managementAdapter,
        topSitesAdapter = topSitesAdapter,
        idleAdapter = idleAdapter,
        ttsAdapter = ttsAdapter,
        searchAdapter = searchAdapter,
        alarmsAdapter = alarmsAdapter,
        systemAdapter = systemAdapter,
        notificationsAdapter = notificationsAdapter,
        context = context,
        delegate = delegate
    )

    var onWorkerCrash: ((String, String) -> Unit)? = null

    init {
        permissionsAdapter.setRegistry(registry)
        permissionsAdapter.setEventManager(eventManager)
        alarmsAdapter.serviceWorkerEventDispatcher = serviceWorkerEventDispatcher
        messageBus.registerListener(this)
        messageBus.registerPortListener(this)
    }

    fun buildSender(extensionId: String): ExtensionSender {
        val url = webView?.url
        val isExtUrl = url?.startsWith("chrome-extension://") == true || url?.startsWith("sw-extension://") == true
        val contextType = when {
            isExtUrl -> {
                val lower = url.lowercase()
                when {
                    lower.contains("popup") -> ExtensionContextType.POPUP
                    lower.contains("options") -> ExtensionContextType.OPTIONS
                    lower.contains("sidepanel") || lower.contains("side_panel") -> ExtensionContextType.SIDE_PANEL
                    lower.contains("devtools") -> ExtensionContextType.DEVTOOLS
                    lower.contains("background") -> ExtensionContextType.BACKGROUND
                    else -> ExtensionContextType.EXTENSION_PAGE
                }
            }
            tabId != null -> ExtensionContextType.CONTENT_SCRIPT
            else -> ExtensionContextType.WEB_PAGE
        }
        val origin = if (url != null && url.startsWith("http")) {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}${if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
        } else url

        return ExtensionSender(
            extensionId = extensionId,
            tabId = tabId ?: delegate?.getActiveTabId(),
            windowId = null,
            frameId = null,
            documentId = null,
            url = url,
            origin = origin,
            contextType = contextType,
            isPrivate = isPrivate,
            privateSessionId = privateSessionId
        )
    }

    private fun validateCaller(extensionId: String): Boolean {
        val cleanId = extensionId.lowercase().trim()
        if (cleanId.isBlank()) return false

        val ext = registry.getExtension(cleanId) ?: return false
        if (!registry.isExtensionEnabled(cleanId)) return false

        if (isPrivate && !permissionsAdapter.isAllowedInPrivate(cleanId)) {
            return false
        }

        val url = webView?.url ?: return false

        val lowerUrl = url.lowercase().trim()
        if (lowerUrl.startsWith("file://") || lowerUrl.startsWith("content://")) {
            return false
        }

        val urlResult = com.swift.browser.extensionengine.origin.ExtensionUrl.parseExtensionUrl(url)
        if (urlResult != null && urlResult.extensionId.equals(cleanId, ignoreCase = true)) {
            if (urlResult.isSandbox || com.swift.browser.extensionengine.origin.ExtensionOriginValidator.validate(
                    extensionId = cleanId,
                    urlStr = url,
                    expectedContext = com.swift.browser.extensionengine.security.ExtensionPageType.EXTENSION_PAGE,
                    isPrivate = isPrivate,
                    registry = registry,
                    permissionManager = permissionManager
                ) == com.swift.browser.extensionengine.origin.ExtensionOriginValidator.ValidationResult.SANDBOX_PAGE_ISOLATED
            ) {
                return false
            }
            return true
        }

        return permissionManager.hasHostPermission(cleanId, ext.hostPermissions, ext.permissions, url)
    }

    @JavascriptInterface
    fun postMessage(payloadJsonStr: String, callbackId: String) {
        val root = try {
            JSONObject(payloadJsonStr)
        } catch (e: Exception) {
            return
        }

        val api = root.optString("api", "")
        val extensionId = root.optString("extensionId", "")
        val args = root.optJSONArray("args") ?: JSONArray()
        val runtimeGenerationId = root.optString("runtimeGenerationId", "gen1")

        mainScope.launch {
            try {
                if (!validateCaller(extensionId)) {
                    sendErrorResponse(callbackId, "SecurityError: Invalid execution context or missing host permission for extension '$extensionId'.")
                    return@launch
                }
                handleApiCall(api, extensionId, args, callbackId, runtimeGenerationId)
            } catch (e: Exception) {
                e.printStackTrace()
                sendErrorResponse(callbackId, e.localizedMessage ?: e.toString())
            }
        }
    }

    private suspend fun handleApiCall(
        api: String,
        extensionId: String,
        args: JSONArray,
        callbackId: String,
        runtimeGenerationId: String
    ) {
        val sender = buildSender(extensionId)

        when (api) {
            "event.addListener" -> {
                val eventName = args.optString(0, "")
                val listenerId = args.optString(1, "")
                val filter = args.optJSONObject(2)
                val extraSpec = args.optJSONArray(3)
                jsEventRegistry.addListener(extensionId, runtimeGenerationId, eventName, listenerId, filter, extraSpec)
                sendSuccessResponse(callbackId, JSONObject().put("status", "added"))
            }
            "event.removeListener" -> {
                val eventName = args.optString(0, "")
                val listenerId = args.optString(1, "")
                jsEventRegistry.removeListener(extensionId, eventName, listenerId)
                sendSuccessResponse(callbackId, JSONObject().put("status", "removed"))
            }
            "runtime.sendMessage" -> {
                val targetExtId = if (args.length() > 1 && args.opt(0) is String) args.optString(0) else extensionId
                val rawMsg = if (args.length() > 1 && args.opt(0) is String) args.opt(1) else args.opt(0)
                val message = if (rawMsg is JSONObject) rawMsg else JSONObject().put("__is_wrapped__", true).put("value", rawMsg)

                val router = messageRouter
                if (router != null) {
                    router.handleSendMessage(
                        sender = sender,
                        targetExtensionId = targetExtId,
                        payload = message,
                        callbackId = callbackId,
                        expectsResponse = callbackId.isNotBlank()
                    ) { response, error ->
                        if (error != null) {
                            sendErrorResponse(callbackId, error)
                        } else {
                            sendSuccessResponse(callbackId, response ?: JSONObject())
                        }
                    }
                } else {
                    messageBus.broadcastMessage(sender, message, callbackId)
                }
            }
            "runtime.response" -> {
                val targetCallbackId = args.optString(0, "")
                val responseData = args.opt(1) ?: JSONObject()
                val router = messageRouter
                if (router != null) {
                    router.handleSendResponse(extensionId, targetCallbackId, responseData)
                } else {
                    messageBus.broadcastResponse(extensionId, targetCallbackId, responseData)
                }
            }
            "tabs.sendMessage" -> {
                val targetTabIdRaw = args.opt(0)
                val rawMsg = args.opt(1)
                val message = if (rawMsg is JSONObject) rawMsg else JSONObject().put("__is_wrapped__", true).put("value", rawMsg)
                val targetTabId = TabIdMapper.getUuidFromString(targetTabIdRaw?.toString() ?: "")

                val router = messageRouter
                if (router != null) {
                    router.handleSendMessage(
                        sender = sender,
                        targetExtensionId = extensionId,
                        payload = message,
                        callbackId = callbackId,
                        expectsResponse = callbackId.isNotBlank()
                    ) { response, error ->
                        if (error != null) {
                            sendErrorResponse(callbackId, error)
                        } else {
                            sendSuccessResponse(callbackId, response ?: JSONObject())
                        }
                    }
                } else {
                    messageBus.broadcastMessage(sender, message, callbackId, targetTabId)
                }
            }
            "runtime.portConnect", "tabs.connect" -> {
                val targetExtId = args.optString(0, extensionId)
                val channelId = args.optString(1, "")
                val portName = args.optString(2, "")
                val targetTabId = if (api == "tabs.connect") TabIdMapper.getUuidFromString(args.optString(0, "")) else null

                if (portManager != null) {
                    portManager.connect(targetExtId, channelId, portName, sender.extensionId, sender, targetTabId)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "connected"))
            }
            "runtime.portPostMessage" -> {
                val channelId = args.optString(0, "")
                val rawMsg = args.opt(1)
                val message = if (rawMsg is JSONObject) rawMsg else JSONObject().put("__is_wrapped__", true).put("value", rawMsg)

                if (portManager != null) {
                    portManager.postMessage(channelId, message)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "sent"))
            }
            "runtime.portDisconnect" -> {
                val channelId = args.optString(0, "")
                if (portManager != null) {
                    portManager.disconnect(channelId)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "disconnected"))
            }
            "runtime.reportCrash" -> {
                val reason = args.optString(0, "unknown")
                onWorkerCrash?.invoke(extensionId, reason)
                sendSuccessResponse(callbackId, JSONObject().put("status", "reported"))
            }
            else -> {
                val result = jsRouter.handleCall(sender, api, args, isPrivate, privateSessionId)
                if (result != null) {
                    sendSuccessResponse(callbackId, result)
                } else {
                    sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
                }
            }
        }
    }

    val ownExtensionId: String? by lazy {
        val url = webView?.url ?: return@lazy null
        val res = com.swift.browser.extensionengine.origin.ExtensionUrl.parseExtensionUrl(url)
        res?.extensionId
    }

    override fun onStructuredMessageReceived(
        sender: ExtensionSender,
        message: JSONObject,
        callbackId: String?,
        targetTabId: String?
    ) {
        val view = webView ?: return
        val tab = tabId

        val targetExtensionId = if (message.has("__targetExtensionId__")) message.optString("__targetExtensionId__") else null
        val realPayload = message.optJSONObject("__payload__") ?: message

        val ownExtId = ownExtensionId
        if (ownExtId != null) {
            if (targetExtensionId != null && !ownExtId.equals(targetExtensionId, ignoreCase = true)) {
                return
            }
        } else {
            if (targetTabId != null) {
                if (tab != targetTabId) return
            } else {
                if (tab != null) return
            }
        }

        view.post {
            try {
                val isWrapped = realPayload.optBoolean("__is_wrapped__", false)
                val msgStr = if (isWrapped) {
                    val wrappedVal = realPayload.opt("value")
                    if (wrappedVal is String) {
                        JSONObject.quote(wrappedVal)
                    } else {
                        wrappedVal?.toString() ?: "null"
                    }
                } else {
                    realPayload.toString()
                }
                val senderJson = sender.toJSONObject().toString()
                val cbParam = if (callbackId != null) "'$callbackId'" else "null"
                val targetExtToPass = targetExtensionId ?: ownExtId ?: sender.extensionId

                view.evaluateJavascript(
                    "if(window._extOnMessage) { window._extOnMessage('$targetExtToPass', $msgStr, $senderJson, $cbParam); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onMessageReceived(extensionId: String, senderTabId: String?, message: JSONObject, callbackId: String?, targetTabId: String?) {
        val sender = ExtensionSender(extensionId = extensionId, tabId = senderTabId)
        onStructuredMessageReceived(sender, message, callbackId, targetTabId)
    }

    override fun onResponseReceived(extensionId: String, callbackId: String, response: Any) {
        val ownExtId = ownExtensionId
        if (ownExtId != null && !ownExtId.equals(extensionId, ignoreCase = true)) {
            return
        }
        val view = webView ?: return
        view.post {
            try {
                val resStr = if (response is String) {
                    JSONObject.quote(response)
                } else {
                    response.toString()
                }
                view.evaluateJavascript(
                    "if(window._extResponse) { window._extResponse('$callbackId', null, $resStr); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPortConnect(extensionId: String, channelId: String, portName: String, senderId: String) {
        val view = webView ?: return
        view.post {
            try {
                view.evaluateJavascript(
                    "if(window._extPortConnect) { window._extPortConnect('$channelId', '$portName', '$senderId'); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPortMessage(channelId: String, message: JSONObject) {
        val view = webView ?: return
        view.post {
            try {
                val isWrapped = message.optBoolean("__is_wrapped__", false)
                val msgStr = if (isWrapped) {
                    val wrappedVal = message.opt("value")
                    if (wrappedVal is String) {
                        JSONObject.quote(wrappedVal)
                    } else {
                        wrappedVal?.toString() ?: "null"
                    }
                } else {
                    message.toString()
                }
                view.evaluateJavascript(
                    "if(window._extPortMessage) { window._extPortMessage('$channelId', $msgStr); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPortDisconnect(channelId: String) {
        val view = webView ?: return
        view.post {
            try {
                view.evaluateJavascript(
                    "if(window._extPortDisconnect) { window._extPortDisconnect('$channelId'); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendSuccessResponse(callbackId: String, result: Any) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val resStr = result.toString()
                webView?.evaluateJavascript("window._extResponse('$callbackId', null, $resStr)", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendErrorResponse(callbackId: String, errorMsg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val errObj = JSONObject().put("error", errorMsg).toString()
                webView?.evaluateJavascript("window._extResponse('$callbackId', $errObj, null)", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
