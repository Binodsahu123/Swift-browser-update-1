package com.swift.browser.extensionengine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated JS execution environment for Manifest V3 Extension Service Workers.
 * Isolated from page DOM, page windows, and raw native bridge instances.
 */
class ServiceWorkerJsRuntime(
    private val context: Context,
    private val scriptInjector: ScriptInjector = ScriptInjector(),
    private val messageBus: MessageBus = MessageBus(),
    private var runtimeBridgeProvider: ((WebView) -> Any)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    data class ActiveWorkerInstance(
        val extensionId: String,
        val generationId: Int,
        val webView: WebView,
        val scriptPath: String,
        val startTime: Long = System.currentTimeMillis(),
        var isInitialized: Boolean = false,
        var startupCallback: ((WorkerStartupResult, String?) -> Unit)? = null
    )

    private val activeWorkers = ConcurrentHashMap<String, ActiveWorkerInstance>()

    var onWorkerCrash: ((String, String) -> Unit)? = null

    fun setRuntimeBridgeProvider(provider: (WebView) -> Any) {
        this.runtimeBridgeProvider = provider
    }

    /**
     * Spawns a dedicated Service Worker runtime instance for the specified extension.
     */
    fun startWorker(
        parsedExtension: ParsedExtension,
        scriptPath: String,
        generationId: Int,
        bootstrapScript: String,
        isPrivate: Boolean = false,
        onStartupResult: (WorkerStartupResult, String?) -> Unit
    ) {
        val extensionId = parsedExtension.id

        // Destroy existing instance if any
        stopWorker(extensionId)

        mainHandler.post {
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.databaseEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                }

                val bridge = runtimeBridgeProvider?.invoke(webView)
                if (bridge != null) {
                    webView.addJavascriptInterface(bridge, "SwiftExtensionBridge")
                }

                val instance = ActiveWorkerInstance(
                    extensionId = extensionId,
                    generationId = generationId,
                    webView = webView,
                    scriptPath = scriptPath,
                    startupCallback = onStartupResult
                )
                activeWorkers[extensionId] = instance

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: ""
                        if (msg.contains("[SW_STARTUP_SUCCESS]")) {
                            handleStartupSuccess(extensionId, generationId)
                        } else if (msg.contains("[SW_STARTUP_FAILED]")) {
                            val err = msg.substringAfter("[SW_STARTUP_FAILED]").trim()
                            handleStartupFailed(extensionId, generationId, err)
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (url.startsWith("chrome-extension://") || url.startsWith("https://")) {
                            val resp = ExtensionDirectoryResolver.handleExtensionRequest(context, url, isPrivate)
                            if (resp != null) return resp
                        }
                        // Strictly block arbitrary external network requests inside service worker runtime
                        return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", mapOf(), ByteArrayInputStream(byteArrayOf()))
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        val reason = if (detail?.didCrash() == true) "process_crash" else "process_killed"
                        stopWorker(extensionId)
                        onWorkerCrash?.invoke(extensionId, reason)
                        return true
                    }
                }

                // Compile worker code sandbox script
                val scriptContent = loadExtensionResource(context, parsedExtension, scriptPath)

                val workerSandboxWrapper = compileWorkerSandbox(
                    extensionId = extensionId,
                    scriptPath = scriptPath,
                    generationId = generationId,
                    bootstrapScript = bootstrapScript,
                    scriptContent = scriptContent
                )

                webView.loadDataWithBaseURL(
                    "chrome-extension://$extensionId/",
                    "<!DOCTYPE html><html><head><script>$workerSandboxWrapper</script></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null
                )

                // Enforce a 10.0s startup timeout
                mainHandler.postDelayed({
                    val cur = activeWorkers[extensionId]
                    if (cur != null && cur.generationId == generationId && !cur.isInitialized) {
                        handleStartupFailed(extensionId, generationId, "Worker startup timed out after 10000ms")
                    }
                }, 10000L)

            } catch (e: Exception) {
                onStartupResult(WorkerStartupResult.FAILED, e.message ?: "Failed to spawn worker webview")
            }
        }
    }

    private fun handleStartupSuccess(extensionId: String, generationId: Int) {
        val instance = activeWorkers[extensionId] ?: return
        if (instance.generationId != generationId) return
        if (instance.isInitialized) return

        instance.isInitialized = true
        val callback = instance.startupCallback
        instance.startupCallback = null
        callback?.invoke(WorkerStartupResult.SUCCESS, null)
    }

    private fun handleStartupFailed(extensionId: String, generationId: Int, errorMsg: String) {
        val instance = activeWorkers[extensionId] ?: return
        if (instance.generationId != generationId) return

        val callback = instance.startupCallback
        instance.startupCallback = null
        stopWorker(extensionId)
        callback?.invoke(WorkerStartupResult.FAILED, errorMsg)
    }

    /**
     * Dispatches an event to an active worker JS execution context.
     */
    fun evaluateEvent(
        extensionId: String,
        generationId: Int,
        eventName: String,
        payload: Any?,
        callback: ((Boolean) -> Unit)? = null
    ) {
        mainHandler.post {
            val instance = activeWorkers[extensionId]
            if (instance == null || instance.generationId != generationId || !instance.isInitialized) {
                callback?.invoke(false)
                return@post
            }

            val payloadStr = when (payload) {
                is JSONObject -> payload.toString()
                is String -> JSONObject.quote(payload)
                null -> "null"
                else -> payload.toString()
            }

            val js = "if (typeof _swiftDispatchEvent === 'function') { _swiftDispatchEvent('$extensionId', '$eventName', $payloadStr); }"
            instance.webView.evaluateJavascript(js) { res ->
                callback?.invoke(res != null && res != "null")
            }
        }
    }

    fun stopWorker(extensionId: String) {
        mainHandler.post {
            val instance = activeWorkers.remove(extensionId) ?: return@post
            try {
                instance.webView.stopLoading()
                instance.webView.removeJavascriptInterface("SwiftExtensionBridge")
                instance.webView.loadUrl("about:blank")
                instance.webView.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopAll() {
        mainHandler.post {
            val keys = ArrayList(activeWorkers.keys)
            for (key in keys) {
                stopWorker(key)
            }
        }
    }

    fun isWorkerRunning(extensionId: String): Boolean {
        val instance = activeWorkers[extensionId] ?: return false
        return instance.isInitialized
    }

    fun getWorkerGeneration(extensionId: String): Int {
        return activeWorkers[extensionId]?.generationId ?: 0
    }

    private fun compileWorkerSandbox(
        extensionId: String,
        scriptPath: String,
        generationId: Int,
        bootstrapScript: String,
        scriptContent: String
    ): String {
        val cleanScriptPath = if (scriptPath.startsWith("/")) scriptPath else "/$scriptPath"

        return """
            (function() {
                'use strict';
                
                // Define worker execution flags
                window._isServiceWorkerContext = true;
                
                // Construct self context object
                var selfObj = {
                    name: "$extensionId",
                    location: {
                        href: "chrome-extension://$extensionId$cleanScriptPath",
                        origin: "chrome-extension://$extensionId",
                        protocol: "chrome-extension:",
                        host: "$extensionId",
                        hostname: "$extensionId",
                        port: "",
                        pathname: "$cleanScriptPath",
                        search: "",
                        hash: ""
                    },
                    registration: {
                        scope: "chrome-extension://$extensionId/",
                        active: true
                    },
                    addEventListener: function(type, listener) {
                        window.addEventListener(type, listener);
                    },
                    removeEventListener: function(type, listener) {
                        window.removeEventListener(type, listener);
                    },
                    importScripts: function() {
                        for (var i = 0; i < arguments.length; i++) {
                            var url = arguments[i];
                            if (!url) continue;
                            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
                                throw new Error("SecurityError: importScripts from remote HTTP/HTTPS origins is strictly prohibited in Manifest V3 Service Workers.");
                            }
                            var resolvedUrl = url;
                            if (!url.startsWith("chrome-extension://")) {
                                if (url.startsWith("/")) {
                                    resolvedUrl = "chrome-extension://$extensionId" + url;
                                } else {
                                    resolvedUrl = "chrome-extension://$extensionId/" + url;
                                }
                            }
                            // Synchronous fetch of extension package asset via XHR
                            try {
                                var xhr = new XMLHttpRequest();
                                xhr.open("GET", resolvedUrl, false);
                                xhr.send();
                                if (xhr.status === 200 || xhr.status === 0) {
                                    eval(xhr.responseText);
                                } else {
                                    throw new Error("Failed to load script: " + url + " (Status " + xhr.status + ")");
                                }
                            } catch (e) {
                                console.error("[IMPORT_SCRIPTS_ERROR]", e);
                                throw e;
                            }
                        }
                    }
                };
                
                // Install bootstrap extension API router
                $bootstrapScript
                
                var extCtx = window._swiftGetExtensionContext ? window._swiftGetExtensionContext("$extensionId") : {};
                selfObj.chrome = extCtx;
                selfObj.browser = extCtx;
                
                // Bind selfObj as global self
                window.self = selfObj;
                window.globalThis = selfObj;

                // Mask DOM and Window attributes to isolate Service Worker environment
                window.document = undefined;
                window.localStorage = undefined;
                window.sessionStorage = undefined;
                window.alert = undefined;
                window.prompt = undefined;
                window.confirm = undefined;

                // Remove raw native bridge exposure from worker JS scope
                var rawBridge = window.SwiftExtensionBridge;
                try {
                    delete window.SwiftExtensionBridge;
                    delete selfObj.SwiftExtensionBridge;
                } catch(e) {}

                // Execute service worker script in worker global context
                try {
                    (function(self, chrome, browser, importScripts, globalThis) {
                        $scriptContent
                    }).call(selfObj, selfObj, selfObj.chrome, selfObj.browser, selfObj.importScripts, selfObj);
                    
                    console.log("[SW_STARTUP_SUCCESS] Generation $generationId for $extensionId");
                } catch (e) {
                    console.error("[SW_STARTUP_FAILED] " + (e.stack || e.message || e));
                }
            })();
        """.trimIndent()
    }

    private fun loadExtensionResource(
        context: Context,
        parsedExtension: ParsedExtension,
        relativePath: String
    ): String {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, parsedExtension.id, parsedExtension.name)
        val file = ExtensionDirectoryResolver.findFileCaseInsensitive(extDir, relativePath)
        return if (file != null && file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                ""
            }
        } else ""
    }
}
