package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import java.io.File
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.swift.browser.tabengine.api.TabEngineApi
import com.swift.browser.tabengine.api.TabEngineProvider

class ExtensionEngineImpl(
    private val context: Context,
    private val delegate: BrowserDelegate?
) : ExtensionEngine {

    val database = ExtensionDatabase.getInstance(context)
    val registry = ExtensionRegistry()
    val parser = ManifestParser()
    val loader = ExtensionLoader(context, parser, database)
    val permissionManager = PermissionManager(context)
    val permissionAdapter = ExtensionPermissionAdapter(context)
    val storageManager = StorageManager(database, context, registry)
    val messageBus = MessageBus()
    val eventManager = EventManager(messageBus)
    val dnrAdapter = ExtensionDnrAdapter(permissionAdapter, registry)
    val webRequestAdapter = ExtensionWebRequestAdapter(permissionAdapter, registry, eventManager, dnrAdapter)
    val popupManager = PopupManager()
    val updateManager = UpdateManager(context = context, registry = registry)
    val scriptInjector = ScriptInjector()
    val cssInjector = CssInjector()
    val contentScriptManager = ContentScriptManager(
        context = context,
        permissionManager = permissionManager,
        scriptInjector = scriptInjector,
        cssInjector = cssInjector,
        extensionRegistry = registry,
        generationLookup = { id -> registry.getExtensionGeneration(id) }
    )
    val backgroundScriptManager = BackgroundScriptManager(context, scriptInjector, messageBus)

    val portManager = PortManager(messageBus)

    val tabEngineApi: TabEngineApi by lazy {
        TabEngineProvider.getEngine(context, ioScope)
    }

    val tabsAdapter by lazy {
        ExtensionTabsAdapter(
            tabEngine = tabEngineApi,
            registry = registry,
            permissionManager = permissionManager,
            messageBus = messageBus,
            portManager = portManager
        )
    }

    val windowsAdapter by lazy {
        ExtensionWindowsAdapter(
            tabEngine = tabEngineApi,
            tabsAdapter = tabsAdapter,
            registry = registry,
            permissionManager = permissionManager
        )
    }

    val tabGroupsAdapter by lazy {
        ExtensionTabGroupsAdapter(
            tabEngine = tabEngineApi,
            registry = registry,
            permissionManager = permissionManager
        )
    }

    val sessionsAdapter by lazy {
        ExtensionSessionsAdapter(
            tabEngine = tabEngineApi,
            tabsAdapter = tabsAdapter,
            registry = registry,
            permissionManager = permissionManager
        )
    }
    val cookieAdapter by lazy {
        ExtensionCookieAdapter(
            context = context,
            permissionAdapter = permissionAdapter,
            registry = registry,
            eventManager = eventManager,
            tabEngine = tabEngineApi
        )
    }

    val bookmarksAdapter by lazy {
        ExtensionBookmarksAdapter(
            context = context,
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val historyAdapter by lazy {
        ExtensionHistoryAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val downloadsAdapter by lazy {
        ExtensionDownloadsAdapter(
            context = context,
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val scriptingAdapter by lazy {
        ExtensionScriptingAdapter(
            permissionAdapter = permissionAdapter,
            registry = registry,
            contentScriptManager = contentScriptManager
        )
    }

    val actionAdapter by lazy {
        ExtensionActionAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val contextMenusAdapter by lazy {
        ExtensionContextMenusAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val commandsAdapter by lazy {
        ExtensionCommandsAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val omniboxAdapter by lazy {
        ExtensionOmniboxAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val sidePanelAdapter by lazy {
        ExtensionSidePanelAdapter(
            permissionManager = permissionManager,
            registry = registry
        )
    }

    val managementAdapter by lazy {
        ExtensionManagementAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }

    val topSitesAdapter by lazy {
        ExtensionTopSitesAdapter(
            permissionManager = permissionManager,
            registry = registry
        )
    }

    val idleAdapter by lazy {
        ExtensionIdleAdapter(
            permissionManager = permissionManager,
            registry = registry
        )
    }

    val ttsAdapter by lazy {
        ExtensionTtsAdapter(
            permissionManager = permissionManager,
            registry = registry
        )
    }

    val searchAdapter by lazy {
        ExtensionSearchAdapter(
            permissionManager = permissionManager,
            registry = registry
        )
    }

    val alarmsAdapter by lazy {
        ExtensionAlarmsAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        ).also {
            it.serviceWorkerEventDispatcher = swEventDispatcher
        }
    }

    val systemAdapter by lazy {
        ExtensionSystemAdapter(
            permissionManager = permissionManager,
            registry = registry
        )
    }

    val notificationsAdapter by lazy {
        ExtensionNotificationsAdapter(
            permissionManager = permissionManager,
            registry = registry,
            eventManager = eventManager
        )
    }
    val activeTabManager = ActiveTabManager(delegate)
    val tabMessenger = TabMessenger(messageBus)
    val tabBridge = TabBridge(tabMessenger, activeTabManager, portManager)
    val pageBridge = PageBridge()
    val domBridge = DomBridge()
    val postMessageBridge = PostMessageBridge()

    val swJsRuntime = ServiceWorkerJsRuntime(context, scriptInjector, messageBus)
    val swRegistry = ServiceWorkerRegistry(context, registry)
    val swWakeController = ServiceWorkerWakeController(
        serviceWorkerRegistry = swRegistry,
        swJsRuntime = swJsRuntime,
        extensionRegistry = registry,
        bootstrapScriptProvider = { extensionId -> compileBootstrapScript(extensionId) },
        permissionAdapter = permissionAdapter
    )
    val swShutdownController = ServiceWorkerShutdownController(
        serviceWorkerRegistry = swRegistry,
        backgroundScriptManager = backgroundScriptManager,
        messageBus = messageBus,
        portManager = portManager,
        swJsRuntime = swJsRuntime
    )
    val swLifecycleManager = ServiceWorkerLifecycleManager(
        serviceWorkerRegistry = swRegistry,
        wakeController = swWakeController,
        shutdownController = swShutdownController,
        extensionRegistry = registry,
        permissionAdapter = permissionAdapter
    )
    val swEventDispatcher = ServiceWorkerEventDispatcher(swRegistry, swWakeController, messageBus, portManager)
    val messageRouter = ExtensionMessageRouter(
        registry = registry,
        permissionManager = permissionManager,
        messageBus = messageBus,
        portManager = portManager,
        swRegistry = swRegistry,
        swWakeController = swWakeController
    )
    val serviceWorkerRuntime = ExtensionServiceWorkerRuntime(
        serviceWorkerRegistry = swRegistry,
        lifecycleManager = swLifecycleManager,
        wakeController = swWakeController,
        shutdownController = swShutdownController,
        eventDispatcher = swEventDispatcher
    )

    private val runtimeMap = mutableMapOf<String, ExtensionRuntime>()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // WebViews that belong to the browser. Weak keys prevent the extension layer
    // from keeping destroyed tabs/WebViews alive. Registration is refreshed whenever
    // the enabled extension set changes.
    private val boundWebViews = java.util.Collections.synchronizedMap(WeakHashMap<WebView, Boolean>())

    private fun refreshBoundWebViews() {
        val snapshot = synchronized(boundWebViews) { boundWebViews.keys.toList() }
        for (webView in snapshot) {
            try {
                contentScriptManager.refreshPersistentScriptsForWebView(webView)
            } catch (e: Throwable) {
                android.util.Log.w("ExtensionEngine", "Failed to refresh content scripts", e)
            }
        }
    }

    init {
        permissionManager.setRegistry(registry)
        permissionAdapter.setRegistry(registry)
        permissionAdapter.setEventManager(eventManager)
        eventManager.serviceWorkerEventDispatcher = swEventDispatcher
        ExtensionDirectoryResolver.globalRegistry = registry
        ExtensionDirectoryResolver.globalPermissionManager = permissionManager

        // Track storage changes and dispatch storage.onChanged event down through EventManager
        storageManager.changeListener = { extensionId, area, changes ->
            val data = org.json.JSONObject().apply {
                put("changes", changes)
                put("areaName", area)
            }
            eventManager.triggerEventForExtension(extensionId, "storage.onChanged", data)
        }

        // Initialize bootstrap provider for intercepting HTML page requests
        ExtensionDirectoryResolver.bootstrapProvider = { id ->
            compileBootstrapScript(id)
        }

        // Initialize bridge provider inside background scripts and service worker runtimes
        backgroundScriptManager.setRuntimeBridgeProvider { webView ->
            createBridge(webView)
        }
        swJsRuntime.setRuntimeBridgeProvider { webView ->
            createBridge(webView)
        }

        // Register worker crash callback to handle WebView / javascript crashes
        backgroundScriptManager.onWorkerCrash = { extensionId, reason ->
            handleServiceWorkerCrash(extensionId, reason)
        }
        swJsRuntime.onWorkerCrash = { extensionId, reason ->
            handleServiceWorkerCrash(extensionId, reason)
        }
        
        // Load installed active extensions asynchronously with a start delay to keep startup instantaneous
        ioScope.launch {
            try {
                // Initialize extension state immediately off the main thread so content-script
                // registrations are available before the first user navigation when possible.
                val dbList = database.extensionDao().getAllExtensions()
                
                // Process database & manifest parsing off the main thread (on Dispatchers.IO)
                val preparedRuntimes = mutableListOf<Pair<ParsedExtension, ExtensionRuntime?>>()
                for (entity in dbList) {
                    try {
                        val parsed = loader.loadFromDatabase(entity)
                        var runtime: ExtensionRuntime? = null
                        if (entity.enabledState) {
                            if (!parsed.isServiceWorker) {
                                runtime = ExtensionRuntime(
                                    parsed, context, backgroundScriptManager,
                                    contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
                                )
                            }
                        }
                        preparedRuntimes.add(Pair(parsed, runtime))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Register and start active extensions on Main thread cleanly
                withContext(Dispatchers.Main) {
                    for (pair in preparedRuntimes) {
                        val parsed = pair.first
                        val runtime = pair.second
                        try {
                            val desiredState = if (parsed.isEnabled) {
                                ExtensionState.INSTALLED_ENABLED
                            } else {
                                ExtensionState.INSTALLED_DISABLED
                            }
                            registry.register(parsed, desiredState)
                            permissionAdapter.onExtensionRegistered(parsed)
                            ExtensionDirectoryResolver.cacheIdAndName(parsed.id, parsed.name)
                            if (parsed.isEnabled && parsed.contentScripts.isNotEmpty()) {
                                contentScriptManager.registerManifestScripts(parsed)
                            }
                            if (!parsed.isServiceWorker) {
                                if (runtime != null) {
                                    runtimeMap[parsed.id] = runtime
                                    runtime.start()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    // The registry was initially populated asynchronously. Any WebView
                    // created during that window may have been configured before extensions
                    // were registered, so refresh native content-script handlers now.
                    refreshBoundWebViews()
                    // Trigger process restart for all MV3 Service Workers
                    swLifecycleManager.onProcessRestart()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Binds a WebView with the extension bridge, allowing injection of API support.
     */
    fun setupWebView(webView: WebView, tabId: String? = null, isPrivate: Boolean = false, privateSessionId: String? = null) {
        val action = {
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                
                val bridge = createBridge(webView, tabId, isPrivate, privateSessionId)
                webView.tag = bridge
                webView.addJavascriptInterface(bridge, "SwiftExtensionBridge")
                synchronized(boundWebViews) { boundWebViews[webView] = true }
                // Register BEFORE the next navigation. WebViewCompat document-start/event
                // scripts only affect frames that begin loading after registration.
                contentScriptManager.registerPersistentScriptsForWebView(webView)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            webView.post(action)
        }
    }

    /**
     * Injects matching scripts on page reload / transition completion.
     */
    fun injectContentScripts(
        webView: WebView,
        url: String,
        runAt: String? = null,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ) {
        if (url.startsWith("swift://") || url == "about:blank" || url.startsWith("file://")) return

        val resolvedPrivate = if (isPrivate) {
            true
        } else {
            try {
                val method = webView.javaClass.getMethod("isPrivate")
                method.invoke(webView) as? Boolean ?: false
            } catch (e: Exception) {
                try {
                    val field = webView.javaClass.getField("isPrivate")
                    field.get(webView) as? Boolean ?: false
                } catch (ex: Exception) {
                    false
                }
            }
        }

        val resolvedSessionId = privateSessionId ?: try {
            val method = webView.javaClass.getMethod("getPrivateSessionId")
            method.invoke(webView) as? String
        } catch (e: Exception) {
            try {
                val field = webView.javaClass.getField("privateSessionId")
                field.get(webView) as? String
            } catch (ex: Exception) {
                null
            }
        }

        val evaluator = object : ScriptEvaluator {
            override fun evaluateJavascript(code: String, callback: ((String?) -> Unit)?) {
                webView.evaluateJavascript(code) { res -> callback?.invoke(res) }
            }
            override fun post(action: () -> Unit) {
                webView.post(action)
            }
        }

        webView.post {
            try {
                val activeList = registry.getAllActiveExtensions()
                val enabledList = activeList.filter { ext ->
                    val isEnabled = runtimeMap[ext.id]?.isActive ?: false
                    isEnabled
                }
                
                // ContentScriptManager handles evaluating and choosing content scripts matching the specified phase
                contentScriptManager.matchAndInject(
                    evaluator = evaluator,
                    url = url,
                    parsedExtensions = enabledList,
                    runAtFilter = runAt ?: "document_idle",
                    bootstrapScriptProvider = { extId -> compileBootstrapScript(extId) },
                    isPrivate = resolvedPrivate,
                    privateSessionId = resolvedSessionId
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun installExtension(uri: Uri): ParsedExtension {
        val parsed = loader.loadAndInstallFromZip(uri)
        registry.register(parsed, if (parsed.isEnabled) ExtensionState.INSTALLED_ENABLED else ExtensionState.INSTALLED_DISABLED)
        permissionAdapter.onExtensionRegistered(parsed)
        ExtensionDirectoryResolver.cacheIdAndName(parsed.id, parsed.name)
        if (parsed.isEnabled && parsed.contentScripts.isNotEmpty()) {
            contentScriptManager.registerManifestScripts(parsed)
        }
        
        withContext(Dispatchers.Main) {
            if (parsed.isServiceWorker) {
                swLifecycleManager.onExtensionInstalled(parsed, "install")
            } else {
                val runtime = ExtensionRuntime(
                    parsed, context, backgroundScriptManager,
                    contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
                )
                runtimeMap[parsed.id] = runtime
                runtime.start()
            }
        }
        refreshBoundWebViews()
        return parsed
    }

    override suspend fun uninstallExtension(id: String) {
        withContext(Dispatchers.Main) {
            if (swRegistry.isRegistered(id)) {
                swLifecycleManager.onExtensionUninstalled(id)
            } else {
                runtimeMap.remove(id)?.stop()
                backgroundScriptManager.stopBackgroundWorker(id)
            }
            ExtensionActionAdapter.cleanupExtensionState(id)
            ExtensionContextMenusAdapter.cleanupExtensionState(id)
            ExtensionCommandsAdapter.cleanupExtensionState(id)
            ExtensionOmniboxAdapter.cleanupExtensionState(id)
            ExtensionSidePanelAdapter.cleanupExtensionState(id)
        }
        permissionAdapter.onExtensionUninstalled(id)
        webRequestAdapter.subscriptionRegistry.removeAllForExtension(id)
        contentScriptManager.registry.unregisterAllForExtension(id)
        contentScriptManager.registry.incrementGeneration()
        registry.unregister(id)
        database.extensionDao().deleteExtensionById(id)
        refreshBoundWebViews()
    }

    override suspend fun toggleExtension(id: String, enabled: Boolean) {
        database.extensionDao().updateEnabledState(id, enabled)

        withContext(Dispatchers.Main) {
            val parsed = registry.getExtension(id)
            if (parsed != null) {
                val targetState = if (enabled) ExtensionState.INSTALLED_ENABLED else ExtensionState.INSTALLED_DISABLED
                try {
                    val currentState = registry.getExtensionState(id)
                    if (currentState != targetState) {
                        registry.transitionState(id, targetState)
                    }
                } catch (_: Exception) {
                    // Keep the persisted DB state authoritative if the in-memory lifecycle
                    // state is already in a terminal/error transition.
                }
                if (enabled && parsed.contentScripts.isNotEmpty()) {
                    contentScriptManager.registerManifestScripts(parsed)
                } else if (!enabled) {
                    contentScriptManager.registry.unregisterAllForExtension(id)
                    contentScriptManager.registry.incrementGeneration()
                }
            }
            if (!enabled) {
                ExtensionActionAdapter.cleanupExtensionState(id)
                ExtensionContextMenusAdapter.cleanupExtensionState(id)
                ExtensionCommandsAdapter.cleanupExtensionState(id)
                ExtensionOmniboxAdapter.cleanupExtensionState(id)
                ExtensionSidePanelAdapter.cleanupExtensionState(id)
            }
            val currentParsed = registry.getExtension(id)
            if (currentParsed != null && currentParsed.isServiceWorker) {
                if (enabled) {
                    swLifecycleManager.onExtensionInstalled(currentParsed, "enable")
                } else {
                    swLifecycleManager.onExtensionDisabled(id)
                }
            } else {
                val runtime = runtimeMap[id]
                if (enabled) {
                    if (runtime != null) {
                        runtime.start()
                    } else {
                        if (currentParsed != null) {
                            val newRuntime = ExtensionRuntime(
                                currentParsed, context, backgroundScriptManager,
                                contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
                            )
                            runtimeMap[currentParsed.id] = newRuntime
                            newRuntime.start()
                        }
                    }
                } else {
                    runtime?.stop()
                }
            }
        }
        refreshBoundWebViews()
    }

    override fun shutdown() {
        val snapshot = synchronized(boundWebViews) { boundWebViews.keys.toList() }
        for (webView in snapshot) {
            try { contentScriptManager.cleanUpWebView(webView) } catch (_: Throwable) {}
        }
        synchronized(boundWebViews) { boundWebViews.clear() }
        backgroundScriptManager.stopAll()
        storageManager.clearPrivateStorage()
    }

    override fun setAllowedInPrivate(id: String, allowed: Boolean) {
        permissionManager.setAllowedInPrivate(id, allowed)
    }

    override fun isAllowedInPrivate(id: String): Boolean {
        return permissionManager.isAllowedInPrivate(id)
    }

    fun clearPrivateSession(privateSessionId: String? = null) {
        storageManager.clearPrivateStorage(privateSessionId)
    }

    fun createBridge(
        webView: WebView?,
        tabId: String? = null,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ): RuntimeBridge {
        val bridge = RuntimeBridge(
            context = context,
            webView = webView,
            storageManager = storageManager,
            messageBus = messageBus,
            delegate = delegate,
            eventManager = eventManager,
            tabId = tabId,
            portManager = portManager,
            tabBridge = tabBridge,
            registry = registry,
            permissionManager = permissionManager,
            isPrivate = isPrivate,
            privateSessionId = privateSessionId,
            tabsAdapter = tabsAdapter,
            windowsAdapter = windowsAdapter,
            tabGroupsAdapter = tabGroupsAdapter,
            sessionsAdapter = sessionsAdapter,
            permissionsAdapter = permissionAdapter,
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
            serviceWorkerEventDispatcher = swEventDispatcher,
            messageRouter = messageRouter
        )
        bridge.onWorkerCrash = { extId, reason ->
            handleServiceWorkerCrash(extId, reason)
        }
        return bridge
    }

    fun handleServiceWorkerCrash(extensionId: String, reason: String) {
        val worker = swRegistry.getWorker(extensionId) ?: return
        
        swRegistry.transitionState(extensionId, ServiceWorkerState.CRASHED)
        worker.crashCount++
        
        val now = System.currentTimeMillis()
        val in60s = now - worker.lastActiveTimestamp < 60_000L
        
        if (in60s && worker.crashCount > 3) {
            swRegistry.transitionState(extensionId, ServiceWorkerState.FAILED)
            ExtensionDebuggerEngine.instance.logError(
                extensionId,
                "Service Worker",
                DebugErrorType.RUNTIME,
                "Service Worker failed due to repeated crashes ($reason)"
            )
        } else {
            if (!in60s) {
                worker.crashCount = 1
            }
            worker.lastActiveTimestamp = now
            worker.restartCount++
            swRegistry.transitionState(extensionId, ServiceWorkerState.RESTART_PENDING)
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (swRegistry.getState(extensionId) == ServiceWorkerState.RESTART_PENDING) {
                    val event = QueuedServiceWorkerEvent(
                        eventId = "restart_${System.currentTimeMillis()}",
                        eventName = "runtime.onStartup",
                        payload = org.json.JSONObject().put("restart", true)
                    )
                    swWakeController.wakeAndExecute(extensionId, event)
                }
            }, 1000L)
        }
    }

    private fun loadExtensionMessagesJson(extensionId: String): String {
        try {
            val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
            val localesDir = File(extensionDir, "_locales")
            if (!localesDir.exists() || !localesDir.isDirectory) {
                return "{}"
            }

            val currentLocale = java.util.Locale.getDefault()
            val langCode = currentLocale.language
            val country = currentLocale.country
            val fullCode = if (country.isNotBlank()) "${langCode}_$country" else langCode

            val candidateDirs = listOf(
                fullCode,
                fullCode.replace("_", "-"),
                langCode,
                "en",
                "en-US",
                "en_US"
            )

            var messagesFile: File? = null
            for (cand in candidateDirs) {
                val f = File(localesDir, cand + "/messages.json")
                if (f.exists() && f.isFile) {
                    messagesFile = f
                    break
                }
            }

            if (messagesFile == null) {
                val subfolders = localesDir.listFiles { f -> f.isDirectory }
                if (subfolders != null) {
                    for (sub in subfolders) {
                        val f = File(sub, "messages.json")
                        if (f.exists() && f.isFile) {
                            messagesFile = f
                            break
                        }
                    }
                }
            }

            if (messagesFile != null && messagesFile.exists()) {
                val fileContent = messagesFile.readText()
                // Validate it's correct JSON
                val testObj = org.json.JSONObject(fileContent)
                return testObj.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "{}"
    }

    fun compileBootstrapScript(extensionId: String): String {
        val ext = registry.getExtension(extensionId)
        val manifestJsonSafe = ext?.manifestJson ?: "{}"
        val quotedManifest = org.json.JSONObject.quote(manifestJsonSafe)
        val messagesJsonSafe = loadExtensionMessagesJson(extensionId)
        val quotedMessages = org.json.JSONObject.quote(messagesJsonSafe)

        val domUtils = domBridge.compileDomUtilities()
        val postMessageUtils = PostMessageBridge().compilePostMessageScript()

        return """
            (function() {
                const extId = "$extensionId";
                
                // Inject DOM and PostMessage Utility Helpers
                $domUtils
                $postMessageUtils
                
                window._swiftManifests = window._swiftManifests || {};
                window._swiftManifests[extId] = $quotedManifest;

                window._swiftExtensionMessages = window._swiftExtensionMessages || {};
                try {
                    window._swiftExtensionMessages[extId] = JSON.parse($quotedMessages);
                } catch(e) {
                    window._swiftExtensionMessages[extId] = {};
                }

                // Bind diagnostic listeners for uncaught errors and promise rejections
                window.addEventListener("error", function(e) {
                    console.error("[UNCAUGHT_ERROR] " + e.message + " (at " + e.filename + ":" + e.lineno + ":" + e.colno + ")");
                });
                window.addEventListener("unhandledrejection", function(e) {
                    console.error("[UNHANDLED_REJECTION] " + (e.reason ? (e.reason.message || e.reason) : "Unknown Promise Rejection"));
                });

                window._extCallbacks = window._extCallbacks || {};
                window._extResponse = function(callbackId, error, result) {
                    const cb = window._extCallbacks[callbackId];
                    if (cb) {
                        delete window._extCallbacks[callbackId];
                        cb(error ? (typeof error === "object" ? error : { error: String(error) }) : result);
                    }
                };

                window._swiftExtensionEvents = window._swiftExtensionEvents || {};
                window._swiftDispatchEvent = function(extId, eventName, data) {
                    const extEvents = window._swiftExtensionEvents[extId] || {};
                    const listeners = extEvents[eventName] || [];
                    let eventArgs = [];
                    if (eventName === "tabs.onUpdated") {
                        const intTabId = data.tabId || 0;
                        const changeInfo = data.changeInfo || {};
                        const tab = data.tab || {};
                        eventArgs = [intTabId, changeInfo, tab];
                    } else if (eventName === "tabs.onActivated") {
                        eventArgs = [data.activeInfo || {}];
                    } else if (eventName === "tabs.onCreated" || eventName === "tabs.onRemoved") {
                        eventArgs = [data.tab || data.tabId || data];
                    } else if (eventName === "storage.onChanged") {
                        const changes = data.changes || {};
                        const areaName = data.areaName || "local";
                        eventArgs = [changes, areaName];
                    } else if (eventName === "webNavigation.onCompleted" || eventName === "webNavigation.onHistoryStateUpdated" || eventName === "webNavigation.onBeforeNavigate" || eventName === "webNavigation.onCommitted" || eventName === "webNavigation.onDOMContentLoaded" || eventName === "webNavigation.onErrorOccurred") {
                        eventArgs = [data.details || data];
                    } else if (eventName === "alarms.onAlarm") {
                        eventArgs = [data.alarm || data];
                    } else if (eventName === "commands.onCommand") {
                        eventArgs = [data.command || data.name || data];
                    } else if (eventName === "cookies.onChanged") {
                        eventArgs = [data.changeInfo || data];
                    } else if (eventName === "downloads.onCreated" || eventName === "downloads.onErased") {
                        eventArgs = [data.downloadItem || data.downloadId || data];
                    } else if (eventName === "downloads.onChanged") {
                        eventArgs = [data.downloadDelta || data];
                    } else if (eventName === "history.onVisited") {
                        eventArgs = [data.historyItem || data];
                    } else if (eventName === "history.onVisitRemoved") {
                        eventArgs = [data.removed || data];
                    } else if (eventName === "permissions.onAdded" || eventName === "permissions.onRemoved") {
                        eventArgs = [data.permissions || data];
                    } else if (eventName.startsWith("webRequest.")) {
                        eventArgs = [data.details || data];
                    } else {
                        eventArgs = [data];
                    }
                    listeners.forEach(cb => {
                        try { cb.apply(null, eventArgs); } catch(e) { console.error("Event execution error for " + eventName, e); }
                    });
                };

                window._swiftGetExtensionContext = window._swiftGetExtensionContext || function(contextExtId) {
                    const bridgeCall = function(apiName, args) {
                        return new Promise((resolve, reject) => {
                            const cbId = "cb_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                            
                            // Safety timeout (5.0 seconds) to prevent any script from freezing the runtime
                            const timeoutId = setTimeout(() => {
                                if (window._extCallbacks[cbId]) {
                                    delete window._extCallbacks[cbId];
                                    console.warn("[BRIDGE_TIMEOUT] Api '" + apiName + "' timed out after 5000ms.");
                                    reject(new Error("Timeout calling " + apiName));
                                }
                            }, 5000);

                            window._extCallbacks[cbId] = function(errObj, res) {
                                clearTimeout(timeoutId);
                                delete window._extCallbacks[cbId];
                                if (errObj) {
                                    const errMsg = errObj.error || errObj.message || String(errObj);
                                    if (window.chrome && window.chrome.runtime) {
                                        window.chrome.runtime.lastError = { message: String(errMsg) };
                                    }
                                    if (errMsg === "UNSUPPORTED_BY_ORION") {
                                        console.warn("[UNSUPPORTED_BY_ORION] Api '" + apiName + "' is not supported by Orion.");
                                    }
                                    reject(new Error(errMsg));
                                } else {
                                    if (window.chrome && window.chrome.runtime) {
                                        window.chrome.runtime.lastError = null;
                                    }
                                    resolve(res !== undefined ? res : null);
                                }
                            };
                            try {
                                if (typeof SwiftExtensionBridge !== 'undefined' && SwiftExtensionBridge.postMessage) {
                                    SwiftExtensionBridge.postMessage(JSON.stringify({
                                        api: apiName,
                                        extensionId: contextExtId,
                                        args: args || []
                                    }), cbId);
                                } else {
                                    clearTimeout(timeoutId);
                                    delete window._extCallbacks[cbId];
                                    reject(new Error("SwiftExtensionBridge unavailable"));
                                }
                            } catch (e) {
                                clearTimeout(timeoutId);
                                delete window._extCallbacks[cbId];
                                console.error("[BRIDGE_ERROR] Error posting message for '" + apiName + "':", e);
                                reject(e);
                            }
                        });
                    };

                    const makeAsyncApi = function(apiName, argFormatter) {
                        return function() {
                            const rawArgs = Array.prototype.slice.call(arguments);
                            let callback = null;
                            if (rawArgs.length > 0 && typeof rawArgs[rawArgs.length - 1] === "function") {
                                callback = rawArgs.pop();
                            }
                            const formattedArgs = argFormatter ? argFormatter(rawArgs) : rawArgs;
                            const p = bridgeCall(apiName, formattedArgs);
                            if (callback) {
                                p.then(res => {
                                    try { callback(res); } catch(e) { console.error("Callback error in " + apiName, e); }
                                }).catch(err => {
                                    try { callback(undefined); } catch(e) { console.error("Callback error in " + apiName, e); }
                                });
                                return undefined;
                            }
                            return p;
                        };
                    };

                    const createEvent = function(extId, eventName) {
                        window._swiftExtensionEvents = window._swiftExtensionEvents || {};
                        window._swiftExtensionEvents[extId] = window._swiftExtensionEvents[extId] || {};
                        window._swiftExtensionEvents[extId][eventName] = window._swiftExtensionEvents[extId][eventName] || [];
                        window._swiftListenerMap = window._swiftListenerMap || new WeakMap();

                        return {
                            addListener: function(cb, filter, extraInfoSpec) {
                                if (typeof cb === "function") {
                                    if (!window._swiftExtensionEvents[extId][eventName].includes(cb)) {
                                        window._swiftExtensionEvents[extId][eventName].push(cb);
                                    }
                                    let listenerId = window._swiftListenerMap.get(cb);
                                    if (!listenerId) {
                                        listenerId = "list_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                                        window._swiftListenerMap.set(cb, listenerId);
                                    }
                                    bridgeCall("event.addListener", [eventName, listenerId, filter || null, extraInfoSpec || null]);
                                }
                            },
                            removeListener: function(cb) {
                                if (window._swiftExtensionEvents[extId][eventName]) {
                                    window._swiftExtensionEvents[extId][eventName] = 
                                        window._swiftExtensionEvents[extId][eventName].filter(l => l !== cb);
                                }
                                let listenerId = window._swiftListenerMap ? window._swiftListenerMap.get(cb) : null;
                                bridgeCall("event.removeListener", [eventName, listenerId || ""]);
                            },
                            hasListener: function(cb) {
                                if (!window._swiftExtensionEvents[extId][eventName]) return false;
                                return window._swiftExtensionEvents[extId][eventName].includes(cb);
                            },
                            hasListeners: function() {
                                return (window._swiftExtensionEvents[extId][eventName] || []).length > 0;
                            }
                        };
                    };

                    const makeStorageArea = function(areaName) {
                        return {
                            get: function(keys, callback) {
                                let resolvedKeys = keys;
                                let resolvedCallback = callback;
                                if (typeof keys === "function") {
                                    resolvedCallback = keys;
                                    resolvedKeys = null;
                                }
                                const p = bridgeCall("storage.get", [areaName, resolvedKeys]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            },
                            set: function(items, callback) {
                                let resolvedCallback = callback;
                                if (typeof items === "function") {
                                    resolvedCallback = items;
                                }
                                const p = bridgeCall("storage.set", [areaName, items]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            },
                            remove: function(keys, callback) {
                                let resolvedKeys = keys;
                                let resolvedCallback = callback;
                                if (typeof keys === "function") {
                                    resolvedCallback = keys;
                                    resolvedKeys = null;
                                }
                                const p = bridgeCall("storage.remove", [areaName, resolvedKeys]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            },
                            clear: function(callback) {
                                const p = bridgeCall("storage.clear", [areaName]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            getBytesInUse: function(keys, callback) {
                                let resolvedKeys = keys;
                                let resolvedCallback = callback;
                                if (typeof keys === "function") {
                                    resolvedCallback = keys;
                                    resolvedKeys = null;
                                }
                                const p = bridgeCall("storage.getBytesInUse", [areaName, resolvedKeys]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            }
                        };
                    };

                    const actionApi = {
                        setIcon: makeAsyncApi("action.setIcon"),
                        setTitle: makeAsyncApi("action.setTitle"),
                        getTitle: makeAsyncApi("action.getTitle"),
                        setPopup: makeAsyncApi("action.setPopup"),
                        getPopup: makeAsyncApi("action.getPopup"),
                        setBadgeText: makeAsyncApi("action.setBadgeText"),
                        getBadgeText: makeAsyncApi("action.getBadgeText"),
                        setBadgeBackgroundColor: makeAsyncApi("action.setBadgeBackgroundColor"),
                        enable: makeAsyncApi("action.enable"),
                        disable: makeAsyncApi("action.disable"),
                        openPopup: makeAsyncApi("action.openPopup"),
                        onClicked: createEvent(contextExtId, "action.onClicked")
                    };

                    const browserActionApi = {
                        setIcon: makeAsyncApi("browserAction.setIcon"),
                        setTitle: makeAsyncApi("browserAction.setTitle"),
                        getTitle: makeAsyncApi("browserAction.getTitle"),
                        setPopup: makeAsyncApi("browserAction.setPopup"),
                        getPopup: makeAsyncApi("browserAction.getPopup"),
                        setBadgeText: makeAsyncApi("browserAction.setBadgeText"),
                        getBadgeText: makeAsyncApi("browserAction.getBadgeText"),
                        setBadgeBackgroundColor: makeAsyncApi("browserAction.setBadgeBackgroundColor"),
                        enable: makeAsyncApi("browserAction.enable"),
                        disable: makeAsyncApi("browserAction.disable"),
                        openPopup: makeAsyncApi("browserAction.openPopup"),
                        onClicked: createEvent(contextExtId, "browserAction.onClicked")
                    };

                    const pageActionApi = {
                        setIcon: makeAsyncApi("pageAction.setIcon"),
                        setTitle: makeAsyncApi("pageAction.setTitle"),
                        getTitle: makeAsyncApi("pageAction.getTitle"),
                        setPopup: makeAsyncApi("pageAction.setPopup"),
                        getPopup: makeAsyncApi("pageAction.getPopup"),
                        enable: makeAsyncApi("pageAction.enable"),
                        disable: makeAsyncApi("pageAction.disable"),
                        onClicked: createEvent(contextExtId, "pageAction.onClicked")
                    };

                    const contextObj = {
                        action: actionApi,
                        browserAction: browserActionApi,
                        pageAction: pageActionApi,
                        sidePanel: {
                            setOptions: makeAsyncApi("sidePanel.setOptions"),
                            getOptions: makeAsyncApi("sidePanel.getOptions"),
                            setPanelBehavior: makeAsyncApi("sidePanel.setPanelBehavior"),
                            getPanelBehavior: makeAsyncApi("sidePanel.getPanelBehavior"),
                            open: makeAsyncApi("sidePanel.open")
                        },
                        contextMenus: {
                            create: function(createProperties, callback) {
                                const p = bridgeCall("contextMenus.create", [createProperties || {}]);
                                if (callback) p.then(callback);
                                return (createProperties && createProperties.id) || ("menu_" + Math.random().toString(36).substring(2, 9));
                            },
                            update: makeAsyncApi("contextMenus.update"),
                            remove: makeAsyncApi("contextMenus.remove"),
                            removeAll: makeAsyncApi("contextMenus.removeAll"),
                            onClicked: createEvent(contextExtId, "contextMenus.onClicked")
                        },
                        commands: {
                            getAll: makeAsyncApi("commands.getAll"),
                            onCommand: createEvent(contextExtId, "commands.onCommand")
                        },
                        omnibox: {
                            setDefaultSuggestion: makeAsyncApi("omnibox.setDefaultSuggestion"),
                            onInputStarted: createEvent(contextExtId, "omnibox.onInputStarted"),
                            onInputChanged: createEvent(contextExtId, "omnibox.onInputChanged"),
                            onInputEntered: createEvent(contextExtId, "omnibox.onInputEntered"),
                            onInputCancelled: createEvent(contextExtId, "omnibox.onInputCancelled")
                        },
                        runtime: {
                            id: contextExtId,
                            lastError: null,
                            getPlatformInfo: function(callback) {
                                const info = { os: "android", arch: "arm", nacl_arch: "arm" };
                                if (callback) callback(info);
                                return Promise.resolve(info);
                            },
                            openOptionsPage: function(callback) {
                                const manifest = contextObj.runtime.getManifest();
                                const optionsPage = manifest.options_ui ? (manifest.options_ui.page || "") : (manifest.options_page || "");
                                if (!optionsPage) {
                                    if (window.chrome && window.chrome.runtime) {
                                        window.chrome.runtime.lastError = { message: "No options page declared in manifest." };
                                    }
                                    if (callback) callback(undefined);
                                    return Promise.reject(new Error("No options page declared in manifest."));
                                }
                                const url = contextObj.runtime.getURL(optionsPage);
                                const p = contextObj.tabs.create({ url: url });
                                if (callback) p.then(callback);
                                return p;
                            },
                            reload: function() {
                                bridgeCall("runtime.reload", []);
                            },
                            getURL: function(path) {
                                if (!path) return "chrome-extension://" + contextExtId + "/";
                                if (path.startsWith("/")) path = path.substring(1);
                                return "chrome-extension://" + contextExtId + "/" + path;
                            },
                            getManifest: function() {
                                const str = window._swiftManifests[contextExtId] || "{}";
                                try { return JSON.parse(str); } catch(e) { return {}; }
                            },
                            setUninstallURL: makeAsyncApi("runtime.setUninstallURL"),
                            getBackgroundPage: function(callback) {
                                let result = undefined;
                                if (window._isMV2BackgroundPage) {
                                    result = window;
                                }
                                if (callback) callback(result);
                                return Promise.resolve(result);
                            },
                            getViews: function(fetchProperties) {
                                if (window._isServiceWorkerContext) return [];
                                return [window];
                            },
                            onInstalled: (function() {
                                const ev = createEvent(contextExtId, "runtime.onInstalled");
                                return {
                                    addListener: function(cb) {
                                        ev.addListener(cb);
                                        if (!window._isServiceWorkerContext) {
                                            setTimeout(() => { try { cb({ reason: "install" }); } catch(e) {} }, 50);
                                        }
                                    },
                                    removeListener: ev.removeListener,
                                    hasListener: ev.hasListener,
                                    hasListeners: ev.hasListeners
                                };
                            })(),
                            onStartup: createEvent(contextExtId, "runtime.onStartup"),
                            onSuspend: createEvent(contextExtId, "runtime.onSuspend"),
                            onSuspendCanceled: createEvent(contextExtId, "runtime.onSuspendCanceled"),
                            onUpdateAvailable: createEvent(contextExtId, "runtime.onUpdateAvailable"),
                            onMessage: createEvent(contextExtId, "runtime.onMessage"),
                            onMessageExternal: createEvent(contextExtId, "runtime.onMessageExternal"),
                            onConnect: createEvent(contextExtId, "runtime.onConnect"),
                            onConnectExternal: createEvent(contextExtId, "runtime.onConnectExternal"),
                            sendMessage: function() {
                                let targetExtensionId = contextExtId;
                                let message = arguments[0];
                                let options = arguments[1];
                                let responseCallback = arguments[2];
                                
                                if (typeof arguments[0] === "string" && arguments.length > 1 && typeof arguments[1] !== "function") {
                                    targetExtensionId = arguments[0];
                                    message = arguments[1];
                                    options = arguments[2];
                                    responseCallback = arguments[3];
                                }
                                
                                if (typeof options === "function") {
                                    responseCallback = options;
                                    options = null;
                                }
                                if (typeof message === "function") {
                                    responseCallback = message;
                                    message = arguments[0];
                                }
                                
                                const p = bridgeCall("runtime.sendMessage", [targetExtensionId, message]);
                                if (responseCallback) {
                                    p.then(res => {
                                        try { responseCallback(res); } catch(e) { console.error("runtime.sendMessage callback error", e); }
                                    });
                                }
                                return p;
                            },
                            connect: function() {
                                let targetExtensionId = contextExtId;
                                let connectInfo = null;
                                
                                if (arguments.length === 1) {
                                    if (typeof arguments[0] === "string") {
                                        targetExtensionId = arguments[0];
                                    } else if (typeof arguments[0] === "object") {
                                        connectInfo = arguments[0];
                                    }
                                } else if (arguments.length >= 2) {
                                    targetExtensionId = arguments[0];
                                    connectInfo = arguments[1];
                                }
                                
                                const portName = (connectInfo && connectInfo.name) || "";
                                const channelId = "port_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                                
                                const port = {
                                    name: portName,
                                    disconnect: function() {
                                        bridgeCall("runtime.portDisconnect", [channelId]);
                                    },
                                    onDisconnect: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    onMessage: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    postMessage: function(msg) {
                                        bridgeCall("runtime.portPostMessage", [channelId, msg]);
                                    }
                                };
                                window._ports = window._ports || {};
                                window._ports[channelId] = port;
                                bridgeCall("runtime.portConnect", [targetExtensionId, channelId, portName]);
                                return port;
                            }
                        },
                        storage: {
                            onChanged: createEvent(contextExtId, "storage.onChanged"),
                            local: makeStorageArea("local"),
                            sync: makeStorageArea("sync"),
                            session: makeStorageArea("session"),
                            managed: makeStorageArea("managed")
                        },
                        tabs: {
                            onUpdated: createEvent(contextExtId, "tabs.onUpdated"),
                            onActivated: createEvent(contextExtId, "tabs.onActivated"),
                            onCreated: createEvent(contextExtId, "tabs.onCreated"),
                            onRemoved: createEvent(contextExtId, "tabs.onRemoved"),
                            get: makeAsyncApi("tabs.get"),
                            query: makeAsyncApi("tabs.query"),
                            create: makeAsyncApi("tabs.create"),
                            remove: makeAsyncApi("tabs.remove"),
                            reload: makeAsyncApi("tabs.reload"),
                            update: makeAsyncApi("tabs.update"),
                            sendMessage: function(tabId, message, options, callback) {
                                const finalCb = (typeof options === "function") ? options : callback;
                                const p = bridgeCall("tabs.sendMessage", [tabId != null ? tabId.toString() : "", message]);
                                if (finalCb) p.then(finalCb);
                                return p;
                            },
                            connect: function(tabId, connectInfo) {
                                const portName = (connectInfo && connectInfo.name) || "";
                                const channelId = "port_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                                
                                const port = {
                                    name: portName,
                                    disconnect: function() {
                                        bridgeCall("runtime.portDisconnect", [channelId]);
                                    },
                                    onDisconnect: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    onMessage: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    postMessage: function(msg) {
                                        bridgeCall("runtime.portPostMessage", [channelId, msg]);
                                    }
                                };
                                window._ports = window._ports || {};
                                window._ports[channelId] = port;
                                bridgeCall("tabs.connect", [tabId != null ? tabId.toString() : "", channelId, portName]);
                                return port;
                            }
                        },
                        windows: {
                            onCreated: createEvent(contextExtId, "windows.onCreated"),
                            onRemoved: createEvent(contextExtId, "windows.onRemoved"),
                            onFocusChanged: createEvent(contextExtId, "windows.onFocusChanged"),
                            get: function(windowId, queryOptions, callback) {
                                if (typeof queryOptions === "function") {
                                    callback = queryOptions;
                                    queryOptions = null;
                                }
                                const populate = queryOptions ? !!queryOptions.populate : false;
                                const p = bridgeCall("windows.get", [windowId, populate]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            getCurrent: function(queryOptions, callback) {
                                if (typeof queryOptions === "function") {
                                    callback = queryOptions;
                                    queryOptions = null;
                                }
                                const populate = queryOptions ? !!queryOptions.populate : false;
                                const p = bridgeCall("windows.getCurrent", [populate]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            getLastFocused: function(queryOptions, callback) {
                                if (typeof queryOptions === "function") {
                                    callback = queryOptions;
                                    queryOptions = null;
                                }
                                const populate = queryOptions ? !!queryOptions.populate : false;
                                const p = bridgeCall("windows.getLastFocused", [populate]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            getAll: function(queryOptions, callback) {
                                if (typeof queryOptions === "function") {
                                    callback = queryOptions;
                                    queryOptions = null;
                                }
                                const populate = queryOptions ? !!queryOptions.populate : false;
                                const p = bridgeCall("windows.getAll", [populate]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            create: makeAsyncApi("windows.create"),
                            update: makeAsyncApi("windows.update"),
                            remove: makeAsyncApi("windows.remove")
                        },
                        tabGroups: {
                            onCreated: createEvent(contextExtId, "tabGroups.onCreated"),
                            onUpdated: createEvent(contextExtId, "tabGroups.onUpdated"),
                            onMoved: createEvent(contextExtId, "tabGroups.onMoved"),
                            onRemoved: createEvent(contextExtId, "tabGroups.onRemoved"),
                            query: function(queryInfo, callback) {
                                if (typeof queryInfo === "function") {
                                    callback = queryInfo;
                                    queryInfo = null;
                                }
                                const p = bridgeCall("tabGroups.query", [queryInfo || {}]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            get: makeAsyncApi("tabGroups.get"),
                            update: function(groupId, updateProperties, callback) {
                                const p = bridgeCall("tabGroups.update", [groupId, updateProperties || {}]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            move: function(groupId, moveProperties, callback) {
                                const p = bridgeCall("tabGroups.move", [groupId, moveProperties || {}]);
                                if (callback) p.then(callback);
                                return p;
                            }
                        },
                        sessions: {
                            onChanged: createEvent(contextExtId, "sessions.onChanged"),
                            restore: function(sessionId, callback) {
                                if (typeof sessionId === "function") {
                                    callback = sessionId;
                                    sessionId = null;
                                }
                                const p = bridgeCall("sessions.restore", sessionId ? [sessionId] : []);
                                if (callback) p.then(callback);
                                return p;
                            },
                            getDevices: makeAsyncApi("sessions.getDevices"),
                            getRecentlyClosed: makeAsyncApi("sessions.getRecentlyClosed")
                        },
                        scripting: {
                            executeScript: function(spec, callback) {
                                if (spec && spec.func && typeof spec.func === "function") {
                                    spec.func = spec.func.toString();
                                }
                                const p = bridgeCall("scripting.executeScript", [spec]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            insertCSS: makeAsyncApi("scripting.insertCSS"),
                            removeCSS: makeAsyncApi("scripting.removeCSS"),
                            registerContentScripts: makeAsyncApi("scripting.registerContentScripts"),
                            unregisterContentScripts: makeAsyncApi("scripting.unregisterContentScripts"),
                            updateContentScripts: makeAsyncApi("scripting.updateContentScripts"),
                            getRegisteredContentScripts: makeAsyncApi("scripting.getRegisteredContentScripts")
                        },
                        cookies: {
                            get: makeAsyncApi("cookies.get"),
                            getAll: makeAsyncApi("cookies.getAll"),
                            set: makeAsyncApi("cookies.set"),
                            remove: makeAsyncApi("cookies.remove"),
                            getAllCookieStores: makeAsyncApi("cookies.getAllCookieStores"),
                            onChanged: createEvent(contextExtId, "cookies.onChanged")
                        },
                        declarativeNetRequest: {
                            updateDynamicRules: makeAsyncApi("declarativeNetRequest.updateDynamicRules"),
                            getDynamicRules: makeAsyncApi("declarativeNetRequest.getDynamicRules"),
                            updateSessionRules: makeAsyncApi("declarativeNetRequest.updateSessionRules"),
                            getSessionRules: makeAsyncApi("declarativeNetRequest.getSessionRules"),
                            getMatchedRules: makeAsyncApi("declarativeNetRequest.getMatchedRules"),
                            isRegexSupported: makeAsyncApi("declarativeNetRequest.isRegexSupported"),
                            setExtensionActionOptions: makeAsyncApi("declarativeNetRequest.setExtensionActionOptions")
                        },
                        webRequest: {
                            onBeforeRequest: createEvent(contextExtId, "webRequest.onBeforeRequest"),
                            onBeforeSendHeaders: createEvent(contextExtId, "webRequest.onBeforeSendHeaders"),
                            onSendHeaders: createEvent(contextExtId, "webRequest.onSendHeaders"),
                            onHeadersReceived: createEvent(contextExtId, "webRequest.onHeadersReceived"),
                            onAuthRequired: createEvent(contextExtId, "webRequest.onAuthRequired"),
                            onResponseStarted: createEvent(contextExtId, "webRequest.onResponseStarted"),
                            onBeforeRedirect: createEvent(contextExtId, "webRequest.onBeforeRedirect"),
                            onCompleted: createEvent(contextExtId, "webRequest.onCompleted"),
                            onErrorOccurred: createEvent(contextExtId, "webRequest.onErrorOccurred")
                        },
                        permissions: {
                            contains: makeAsyncApi("permissions.contains"),
                            request: makeAsyncApi("permissions.request"),
                            remove: makeAsyncApi("permissions.remove"),
                            getAll: makeAsyncApi("permissions.getAll"),
                            onAdded: createEvent(contextExtId, "permissions.onAdded"),
                            onRemoved: createEvent(contextExtId, "permissions.onRemoved")
                        },
                        bookmarks: {
                            get: makeAsyncApi("bookmarks.get"),
                            getChildren: makeAsyncApi("bookmarks.getChildren"),
                            getRecent: makeAsyncApi("bookmarks.getRecent"),
                            getTree: makeAsyncApi("bookmarks.getTree"),
                            getSubTree: makeAsyncApi("bookmarks.getSubTree"),
                            search: makeAsyncApi("bookmarks.search"),
                            create: makeAsyncApi("bookmarks.create"),
                            update: makeAsyncApi("bookmarks.update"),
                            move: makeAsyncApi("bookmarks.move"),
                            remove: makeAsyncApi("bookmarks.remove"),
                            removeTree: makeAsyncApi("bookmarks.removeTree"),
                            onCreated: createEvent(contextExtId, "bookmarks.onCreated"),
                            onRemoved: createEvent(contextExtId, "bookmarks.onRemoved"),
                            onChanged: createEvent(contextExtId, "bookmarks.onChanged"),
                            onMoved: createEvent(contextExtId, "bookmarks.onMoved"),
                            onChildrenReordered: createEvent(contextExtId, "bookmarks.onChildrenReordered"),
                            onImportBegan: createEvent(contextExtId, "bookmarks.onImportBegan"),
                            onImportEnded: createEvent(contextExtId, "bookmarks.onImportEnded")
                        },
                        history: {
                            search: makeAsyncApi("history.search"),
                            getVisits: makeAsyncApi("history.getVisits"),
                            addUrl: makeAsyncApi("history.addUrl"),
                            deleteUrl: makeAsyncApi("history.deleteUrl"),
                            deleteRange: makeAsyncApi("history.deleteRange"),
                            deleteAll: makeAsyncApi("history.deleteAll"),
                            onVisited: createEvent(contextExtId, "history.onVisited"),
                            onVisitRemoved: createEvent(contextExtId, "history.onVisitRemoved")
                        },
                        downloads: {
                            download: makeAsyncApi("downloads.download"),
                            search: makeAsyncApi("downloads.search"),
                            pause: makeAsyncApi("downloads.pause"),
                            resume: makeAsyncApi("downloads.resume"),
                            cancel: makeAsyncApi("downloads.cancel"),
                            removeFile: makeAsyncApi("downloads.removeFile"),
                            erase: makeAsyncApi("downloads.erase"),
                            open: makeAsyncApi("downloads.open"),
                            onCreated: createEvent(contextExtId, "downloads.onCreated"),
                            onErased: createEvent(contextExtId, "downloads.onErased"),
                            onChanged: createEvent(contextExtId, "downloads.onChanged")
                        },
                        notifications: {
                            create: makeAsyncApi("notifications.create"),
                            update: makeAsyncApi("notifications.update"),
                            clear: makeAsyncApi("notifications.clear"),
                            getAll: makeAsyncApi("notifications.getAll"),
                            onClosed: createEvent(contextExtId, "notifications.onClosed"),
                            onClicked: createEvent(contextExtId, "notifications.onClicked"),
                            onButtonClicked: createEvent(contextExtId, "notifications.onButtonClicked")
                        },
                        idle: {
                            queryState: makeAsyncApi("idle.queryState"),
                            setDetectionInterval: makeAsyncApi("idle.setDetectionInterval"),
                            onStateChanged: createEvent(contextExtId, "idle.onStateChanged")
                        },
                        management: {
                            get: makeAsyncApi("management.get"),
                            getSelf: makeAsyncApi("management.getSelf"),
                            getAll: makeAsyncApi("management.getAll"),
                            setEnabled: makeAsyncApi("management.setEnabled"),
                            uninstall: makeAsyncApi("management.uninstall"),
                            onInstalled: createEvent(contextExtId, "management.onInstalled"),
                            onUninstalled: createEvent(contextExtId, "management.onUninstalled"),
                            onEnabled: createEvent(contextExtId, "management.onEnabled"),
                            onDisabled: createEvent(contextExtId, "management.onDisabled")
                        },
                        alarms: {
                            create: function(name, alarmInfo, callback) {
                                let resolvedName = name;
                                let resolvedAlarmInfo = alarmInfo;
                                let resolvedCallback = callback;
                                if (typeof name === "object") {
                                    resolvedCallback = alarmInfo;
                                    resolvedAlarmInfo = name;
                                    resolvedName = "";
                                }
                                if (typeof name === "function") {
                                    resolvedCallback = name;
                                    resolvedAlarmInfo = {};
                                    resolvedName = "";
                                }
                                if (typeof alarmInfo === "function") {
                                    resolvedCallback = alarmInfo;
                                    resolvedAlarmInfo = {};
                                }
                                const p = bridgeCall("alarms.create", [resolvedName, resolvedAlarmInfo]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            },
                            get: function(name, callback) {
                                let resolvedName = name;
                                let resolvedCallback = callback;
                                if (typeof name === "function") {
                                    resolvedCallback = name;
                                    resolvedName = "";
                                }
                                const p = bridgeCall("alarms.get", [resolvedName]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            },
                            getAll: makeAsyncApi("alarms.getAll"),
                            clear: function(name, callback) {
                                let resolvedName = name;
                                let resolvedCallback = callback;
                                if (typeof name === "function") {
                                    resolvedCallback = name;
                                    resolvedName = "";
                                }
                                const p = bridgeCall("alarms.clear", [resolvedName]);
                                if (resolvedCallback) p.then(resolvedCallback);
                                return p;
                            },
                            clearAll: makeAsyncApi("alarms.clearAll"),
                            onAlarm: createEvent(contextExtId, "alarms.onAlarm")
                        },
                        system: {
                            cpu: {
                                getInfo: makeAsyncApi("system.cpu.getInfo")
                            },
                            memory: {
                                getInfo: makeAsyncApi("system.memory.getInfo")
                            },
                            storage: {
                                getInfo: makeAsyncApi("system.storage.getInfo")
                            }
                        },
                        tts: {
                            speak: function(utterance, options, callback) {
                                if (typeof options === "function") {
                                    callback = options;
                                    options = {};
                                }
                                const p = bridgeCall("tts.speak", [utterance, options || {}]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            stop: makeAsyncApi("tts.stop"),
                            pause: makeAsyncApi("tts.pause"),
                            resume: makeAsyncApi("tts.resume"),
                            isSpeaking: makeAsyncApi("tts.isSpeaking"),
                            getVoices: makeAsyncApi("tts.getVoices")
                        },
                        topSites: {
                            get: makeAsyncApi("topSites.get")
                        },
                        search: {
                            query: makeAsyncApi("search.query")
                        },
                        i18n: {
                            getMessage: function(messageName, substitutions) {
                                const msgs = window._swiftExtensionMessages[contextExtId] || {};
                                const item = msgs[messageName] || msgs[messageName.toLowerCase()];
                                if (!item) return messageName;
                                let msg = item.message || "";
                                if (!msg) return messageName;
                                
                                if (substitutions) {
                                    if (!Array.isArray(substitutions)) {
                                        substitutions = [substitutions];
                                    }
                                    substitutions.forEach((sub, i) => {
                                        const phIndex = i + 1;
                                        msg = msg.replace(new RegExp("\\$" + phIndex, "g"), String(sub));
                                    });
                                }
                                
                                if (item.placeholders) {
                                    for (const phName in item.placeholders) {
                                        const phObj = item.placeholders[phName];
                                        const content = phObj.content || "";
                                        let resolvedContent = content;
                                        if (substitutions) {
                                            substitutions.forEach((sub, i) => {
                                                const phIndex = i + 1;
                                                resolvedContent = resolvedContent.replace(new RegExp("\\$" + phIndex, "g"), String(sub));
                                            });
                                        }
                                        msg = msg.split("$" + phName + "$").join(resolvedContent);
                                        msg = msg.split("$" + phName.toLowerCase() + "$").join(resolvedContent);
                                    }
                                }
                                return msg;
                            },
                            getAcceptLanguages: function(callback) {
                                const langs = [navigator.language || "en-US"];
                                if (callback) callback(langs);
                                return Promise.resolve(langs);
                            },
                            getUILanguage: function() {
                                return navigator.language || "en-US";
                            },
                            detectLanguage: function(text, callback) {
                                const res = { isReliable: true, languages: [{ language: "en", percentage: 100 }] };
                                if (callback) callback(res);
                                return Promise.resolve(res);
                            }
                        },
                        extension: {
                            getURL: function(path) {
                                return contextObj.runtime.getURL(path);
                            },
                            getBackgroundPage: function() {
                                return contextObj.runtime.getBackgroundPage();
                            },
                            getViews: function(fetchProperties) {
                                return contextObj.runtime.getViews(fetchProperties);
                            },
                            isAllowedIncognitoAccess: function(callback) {
                                const p = bridgeCall("extension.isAllowedIncognitoAccess", []);
                                if (callback) p.then(callback);
                                return p;
                            },
                            isAllowedFileSchemeAccess: function(callback) {
                                const p = bridgeCall("extension.isAllowedFileSchemeAccess", []);
                                if (callback) p.then(callback);
                                return p;
                            },
                            sendMessage: function(msg, cb) {
                                return contextObj.runtime.sendMessage(msg, cb);
                            },
                            connect: function(info) {
                                return contextObj.runtime.connect(info);
                            },
                            inIncognitoContext: false
                        },
                        webNavigation: {
                            onCompleted: createEvent(contextExtId, "webNavigation.onCompleted"),
                            onHistoryStateUpdated: createEvent(contextExtId, "webNavigation.onHistoryStateUpdated"),
                            onBeforeNavigate: createEvent(contextExtId, "webNavigation.onBeforeNavigate"),
                            onCommitted: createEvent(contextExtId, "webNavigation.onCommitted"),
                            onDOMContentLoaded: createEvent(contextExtId, "webNavigation.onDOMContentLoaded"),
                            onErrorOccurred: createEvent(contextExtId, "webNavigation.onErrorOccurred"),
                            onCreatedNavigationTarget: createEvent(contextExtId, "webNavigation.onCreatedNavigationTarget"),
                            onReferenceFragmentUpdated: createEvent(contextExtId, "webNavigation.onReferenceFragmentUpdated"),
                            onTabReplaced: createEvent(contextExtId, "webNavigation.onTabReplaced")
                        },
                        declarativeContent: {
                            onPageChanged: createEvent(contextExtId, "declarativeContent.onPageChanged"),
                            ShowPageAction: function() { return {}; },
                            PageStateMatcher: function(props) { return props || {}; }
                        }
                    };

                    return contextObj;
                };

                const extContext = window._swiftGetExtensionContext(extId);
                window.browser = extContext;
                window.chrome = extContext;
                if (typeof self !== 'undefined') {
                    self.browser = extContext;
                    self.chrome = extContext;
                }

                window._ports = window._ports || {};

                window._extPortMessage = function(channelId, msg) {
                    const port = window._ports[channelId];
                    if (port) {
                        port.onMessage.listeners.forEach(cb => {
                            try { cb(msg); } catch(e) {}
                        });
                    }
                };

                window._extPortDisconnect = function(channelId) {
                    const port = window._ports[channelId];
                    if (port) {
                        delete window._ports[channelId];
                        port.onDisconnect.listeners.forEach(cb => {
                            try { cb(); } catch(e) {}
                        });
                    }
                };

                window._extPortConnect = function(channelId, portName, senderId) {
                    const port = {
                        name: portName,
                        sender: { id: senderId },
                        disconnect: function() {
                            const bridgeCall = function(apiName, args) {
                                SwiftExtensionBridge.postMessage(JSON.stringify({
                                    api: apiName,
                                    extensionId: extId,
                                    args: args || []
                                }), "dis_" + Date.now());
                            };
                            bridgeCall("runtime.portDisconnect", [channelId]);
                        },
                        onDisconnect: {
                            listeners: [],
                            addListener: function(cb) { this.listeners.push(cb); },
                            removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                        },
                        onMessage: {
                            listeners: [],
                            addListener: function(cb) { this.listeners.push(cb); },
                            removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                        },
                        postMessage: function(msg) {
                            const bridgeCall = function(apiName, args) {
                                SwiftExtensionBridge.postMessage(JSON.stringify({
                                    api: apiName,
                                    extensionId: extId,
                                    args: args || []
                                }), "msg_" + Date.now());
                            };
                            bridgeCall("runtime.portPostMessage", [channelId, msg]);
                        }
                    };
                    window._ports[channelId] = port;
                    
                    const extEvents = window._swiftExtensionEvents[extId] || {};
                    const connListeners = extEvents["runtime.onConnect"] || [];
                    connListeners.forEach(cb => {
                        try { cb(port); } catch(e) {}
                    });
                };

                window._extOnMessage = function(targetExtId, message, sender, callbackId) {
                    if (message && message.type === "EVENT_DISPATCH") {
                        const eventName = message.eventName;
                        const data = message.data || {};
                        window._swiftDispatchEvent(targetExtId, eventName, data);
                        return;
                    }
                    const extEvents = window._swiftExtensionEvents[targetExtId] || {};
                    const listeners = extEvents["runtime.onMessage"] || [];
                    const sendResponse = function(resp) {
                        if (callbackId) {
                            try {
                                SwiftExtensionBridge.postMessage(JSON.stringify({
                                    api: "runtime.response",
                                    extensionId: targetExtId,
                                    args: [callbackId, resp]
                                }), "resp_" + Date.now());
                            } catch(e) {
                                console.error("sendResponse error: ", e);
                            }
                        }
                    };
                    listeners.forEach(cb => {
                        try { cb(message, sender, sendResponse); } catch(e) {}
                    });
                };
            })();
        """.trimIndent()
    }
}

