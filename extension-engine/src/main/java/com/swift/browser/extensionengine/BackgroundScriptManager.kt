package com.swift.browser.extensionengine

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

class BackgroundScriptManager(
    val context: Context,
    val scriptInjector: ScriptInjector,
    val messageBus: MessageBus
) {

    private val backgroundWebViews = mutableMapOf<String, WebView>()
    private var _runtimeBridgeProvider: ((WebView) -> RuntimeBridge)? = null
    val runtimeBridgeProvider: ((WebView) -> RuntimeBridge)? get() = _runtimeBridgeProvider
    var consoleLogCallback: ((level: String, message: String) -> Unit)? = null
    var onWorkerCrash: ((extensionId: String, reason: String) -> Unit)? = null

    fun setRuntimeBridgeProvider(provider: (WebView) -> RuntimeBridge) {
        this._runtimeBridgeProvider = provider
    }

    fun hasBackgroundWorker(id: String): Boolean = backgroundWebViews.containsKey(id)

    /**
     * Instantiates an invisible WebView instance acting as the sandbox execution run-loop
     * for Chrome extension background workers/pages.
     */
    @android.annotation.SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    fun startBackgroundWorker(ext: ParsedExtension, bootstrapScript: String, isPrivate: Boolean = false, permissionManager: PermissionManager? = null) {
        // MV3 service workers are handled exclusively by ServiceWorkerJsRuntime
        if (ext.manifestVersion >= 3 || ext.isServiceWorker) return
        if (ext.backgroundScripts.isEmpty()) return
        if (isPrivate) {
            val allowed = ext.allowedInPrivate || (permissionManager?.isAllowedInPrivate(ext.id) == true)
            if (!allowed) return
        }
        if (backgroundWebViews.containsKey(ext.id)) return

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val wv = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    try {
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // Spoof standalone Chrome browser signature for extensions
                    settings.userAgentString = com.swift.browser.desktopengine.useragent.UserAgentManager.getMobileUserAgent(context)

                    val bridge = runtimeBridgeProvider?.invoke(this)
                    if (bridge != null) {
                        this.tag = bridge
                        addJavascriptInterface(bridge, "SwiftExtensionBridge")
                    }

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            if (consoleMessage != null) {
                                val levelStr = consoleMessage.messageLevel()?.name ?: "LOG"
                                val msg = consoleMessage.message() ?: ""
                                val sourceId = consoleMessage.sourceId() ?: ""
                                val line = consoleMessage.lineNumber()
                                consoleLogCallback?.invoke(levelStr, "$msg ($sourceId:$line)")
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            onWorkerCrash?.invoke(ext.id, "onRenderProcessGone")
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val urlStr = request?.url?.toString() ?: return null
                            return ExtensionDirectoryResolver.handleExtensionRequest(context, urlStr)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            // Load actual background JS scripts
                            val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)
                            for (scriptFile in ext.backgroundScripts) {
                                try {
                                    val code = readExtensionFile(extensionDir, scriptFile)
                                    if (code.isNotBlank()) {
                                        val isMV3Worker = ext.isServiceWorker || (ext.manifestVersion >= 3 && ext.backgroundSpec.serviceWorker.isNotBlank())
                                        val wrapper = if (isMV3Worker) {
                                            """
                                                 (function() {
                                                     window._isServiceWorkerContext = true;
                                                     // Ensure APIs are always loaded and bound before background execution
                                                     if (typeof window._swiftGetExtensionContext === 'undefined') {
                                                         try {
                                                             $bootstrapScript
                                                         } catch(e) {
                                                             console.error("API Bootstrap Failed for MV3 service worker: ", e);
                                                         }
                                                     }
                                                     
                                                     try {
                                                         const browser = window._swiftGetExtensionContext("${ext.id}");
                                                         const chrome = browser;
                                                         
                                                         // Service worker globals sandbox (Strict MV3 isolation: no window/document/DOM/storage)
                                                         const self = {
                                                             chrome: chrome,
                                                             browser: browser,
                                                             registration: { scope: "chrome-extension://${ext.id}/" },
                                                             location: {
                                                                 href: "chrome-extension://${ext.id}/$scriptFile",
                                                                 origin: "chrome-extension://${ext.id}",
                                                                 protocol: "chrome-extension:",
                                                                 pathname: "/$scriptFile"
                                                             },
                                                             addEventListener: function(type, listener) {
                                                                 if (!window._swiftExtensionEvents) window._swiftExtensionEvents = {};
                                                                 if (!window._swiftExtensionEvents["${ext.id}"]) window._swiftExtensionEvents["${ext.id}"] = {};
                                                                 if (!window._swiftExtensionEvents["${ext.id}"][type]) window._swiftExtensionEvents["${ext.id}"][type] = [];
                                                                 window._swiftExtensionEvents["${ext.id}"][type].push(listener);
                                                             },
                                                             removeEventListener: function(type, listener) {
                                                                 if (window._swiftExtensionEvents && window._swiftExtensionEvents["${ext.id}"] && window._swiftExtensionEvents["${ext.id}"][type]) {
                                                                     const arr = window._swiftExtensionEvents["${ext.id}"][type];
                                                                     const idx = arr.indexOf(listener);
                                                                     if (idx >= 0) arr.splice(idx, 1);
                                                                 }
                                                             },
                                                             importScripts: function() {
                                                                 for (let i = 0; i < arguments.length; i++) {
                                                                     const scriptUrl = arguments[i];
                                                                     try {
                                                                         const xhr = new XMLHttpRequest();
                                                                         const fullUrl = scriptUrl.startsWith('chrome-extension://') || scriptUrl.startsWith('http')
                                                                             ? scriptUrl
                                                                             : "chrome-extension://${ext.id}/" + scriptUrl.replace(/^\.?\//, '');
                                                                         xhr.open('GET', fullUrl, false);
                                                                         xhr.send(null);
                                                                         if (xhr.status === 200 || xhr.status === 0) {
                                                                             eval(xhr.responseText);
                                                                         }
                                                                     } catch(err) {
                                                                         console.error("importScripts failed for " + scriptUrl, err);
                                                                     }
                                                                 }
                                                             }
                                                         };
                                                         const globalThis = self;
                                                         
                                                         (function(window, document, localStorage, sessionStorage, alert, prompt, confirm) {
                                                             $code
                                                         }).call(self, undefined, undefined, undefined, undefined, undefined, undefined, undefined);
                                                     } catch(e) {
                                                         console.error("MV3 Service Worker Exec Error in $scriptFile: ", e);
                                                         if (typeof SwiftExtensionBridge !== 'undefined') {
                                                             try {
                                                                 SwiftExtensionBridge.postMessage(JSON.stringify({
                                                                     api: "runtime.reportCrash",
                                                                     extensionId: "${ext.id}",
                                                                     args: [e.message || e.toString()]
                                                                 }), "");
                                                             } catch(err) {}
                                                         }
                                                     }
                                                 })();
                                            """.trimIndent()
                                        } else {
                                            """
                                                 (function() {
                                                     // Ensure APIs are always loaded and bound before background execution
                                                     if (typeof window._swiftGetExtensionContext === 'undefined') {
                                                         try {
                                                             $bootstrapScript
                                                         } catch(e) {
                                                             console.error("API Bootstrap Failed for background script: ", e);
                                                         }
                                                     }
                                                     
                                                     try {
                                                          const browser = window._swiftGetExtensionContext("${ext.id}");
                                                          const chrome = browser;
                                                          
                                                          // Redefine window, self, and globalThis references inside our function scope using Proxies
                                                          const window = new Proxy(globalThis, {
                                                              get(target, prop) {
                                                                  if (prop === 'chrome') return chrome;
                                                                  if (prop === 'browser') return browser;
                                                                  if (prop === 'window' || prop === 'self' || prop === 'globalThis') return window;
                                                                  let val = target[prop];
                                                                  if (typeof val === 'function') {
                                                                      try {
                                                                          return val.bind(target);
                                                                      } catch(e) {
                                                                          return val;
                                                                      }
                                                                  }
                                                                  return val;
                                                              }
                                                          });
                                                          const self = window;
                                                          const globalThis = window;
                                                          
                                                          $code
                                                     } catch(e) {
                                                          console.error("Background Script Exec Error in $scriptFile: ", e);
                                                     }
                                                 })();
                                            """.trimIndent()
                                        }
                                        evaluateJavascript(wrapper, null)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                var backgroundPagePath = ""
                try {
                    val root = org.json.JSONObject(ext.manifestJson)
                    val backgroundObj = root.optJSONObject("background")
                    if (backgroundObj != null) {
                        backgroundPagePath = backgroundObj.optString("page", "")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val finalUrl = if (backgroundPagePath.isNotBlank()) {
                    "chrome-extension://${ext.id}/${backgroundPagePath.removePrefix("/")}"
                } else {
                    "chrome-extension://${ext.id}/_generated_background_page.html"
                }

                backgroundWebViews[ext.id] = wv
                wv.loadUrl(finalUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopBackgroundWorker(extensionId: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            backgroundWebViews.remove(extensionId)?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getBackgroundWebView(extensionId: String): WebView? = backgroundWebViews[extensionId]

    fun stopAll() {
        val keys = backgroundWebViews.keys.toList()
        keys.forEach { stopBackgroundWorker(it) }
    }

    private fun readExtensionFile(extensionDir: File, relativePath: String): String {
        val cleanPath = relativePath.removePrefix("./").removePrefix("/")
        val file = File(extensionDir, cleanPath)
        try {
            val canonicalFile = file.canonicalFile
            val canonicalDir = extensionDir.canonicalFile
            if (!canonicalFile.path.startsWith(canonicalDir.path)) {
                return ""
            }
            if (canonicalFile.exists()) {
                return canonicalFile.readText()
            }
            val filenameOnly = relativePath.substringAfterLast("/")
            val fallbackFile = File(extensionDir, filenameOnly).canonicalFile
            if (!fallbackFile.path.startsWith(canonicalDir.path)) {
                return ""
            }
            if (fallbackFile.exists()) {
                return fallbackFile.readText()
            }
        } catch (e: Exception) {
            return ""
        }
        return ""
    }
}
