package com.swift.browser.browserengine

import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.desktopengine.useragent.UserAgentManager
import com.swift.browser.securityengine.SslWarningState
import com.swift.browser.securityengine.SwiftSecurityEngine
import com.swift.browser.developertoolsengine.InspectorEngine
import com.swift.browser.developertoolsengine.LogLevel

/**
 * Core engine responsible for handling and executing all WebViewClient and WebChromeClient
 * callback logic. Encapsulates page navigation, lifecycle events, security & SSL handling,
 * request interception & AdBlock filtering, permission routing, window creation, and
 * presentation callback behaviors.
 */
class WebViewCoreCallbackEngine {

    companion object {
        private const val TAG = "WebViewCoreCallbackEngine"

        @Volatile
        private var instance: WebViewCoreCallbackEngine? = null

        fun getInstance(): WebViewCoreCallbackEngine {
            return instance ?: synchronized(this) {
                instance ?: WebViewCoreCallbackEngine().also { instance = it }
            }
        }
    }

    // A. Network & Request Interception
    fun shouldInterceptRequest(
        context: Context,
        view: WebView?,
        request: WebResourceRequest?,
        currentDocUrl: String?,
        onAdBlocked: () -> Unit
    ): WebResourceResponse? {
        return WebResourceInterceptionCoordinator.shouldInterceptRequest(context, view, request, currentDocUrl, onAdBlocked)
    }

    // B. Navigation & Page Lifecycle
    fun onPageStarted(
        context: Context,
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
        tabId: String,
        getTab: (String) -> TabModel?,
        updateTabModel: (String, (TabModel) -> TabModel) -> Unit,
        triggerTabUpdatedEvent: (String, String) -> Unit
    ) {
        try {
            val currentTabModel = getTab(tabId)
            val isTabIncognito = currentTabModel?.isPrivate == true || currentTabModel?.isIncognito == true || (view as? BrowserWebView)?.isPrivate == true

            if (url != null) {
                com.swift.browser.analyticscore.AnalyticsCore.trackPageLoad(
                    url = url,
                    isIncognito = isTabIncognito,
                    context = if (isTabIncognito) com.swift.browser.analyticscore.AnalyticsContext.PRIVATE else com.swift.browser.analyticscore.AnalyticsContext.NORMAL
                )
                com.swift.browser.analyticscore.AnalyticsCore.startTimer("nav_$tabId")
                com.swift.browser.browserengine.screencapture.ScreenCaptureManager.onNavigation(tabId, url)
                com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.onNavigation(tabId, url)
            }

            if (url != null && view != null && !url.startsWith("swift://") && !url.startsWith("about:") && !url.startsWith("file://")) {
                val host = Uri.parse(url).host
                if (!host.isNullOrEmpty()) {
                    val isDesktop = com.swift.browser.desktopengine.api.DesktopEngineProvider.api.isDesktopMode(host)
                    com.swift.browser.desktopengine.webview.WebViewDesktopBridge.onNavigationStarted(tabId, url, view)

                    SwiftDeveloperEngine.desktopConnectionState.value = DesktopConnectionState(
                        userAgentApplied = isDesktop,
                        viewportApplied = isDesktop,
                        cssRulesApplied = isDesktop,
                        hostRewriteApplied = false,
                        hostRewriteSkipped = true,
                        desktopPageLoaded = isDesktop
                    )
                }
            }

            val currentUrl = currentTabModel?.url ?: ""
            val finalUrl = if (url == "about:blank") {
                if (currentUrl == "about:blank" || currentUrl.isEmpty() || currentUrl.startsWith("swift://newtab")) {
                    if (isTabIncognito) "swift://newtab-incognito" else "swift://newtab"
                } else {
                    currentUrl
                }
            } else if (url == "swift://newtab" || url == "swift://newtab-incognito") {
                url
            } else {
                url ?: (if (currentUrl.isNotEmpty()) currentUrl else if (isTabIncognito) "swift://newtab-incognito" else "swift://newtab")
            }

            updateTabModel(tabId) {
                it.copy(
                    url = finalUrl,
                    isLoading = true,
                    progress = 10,
                    favicon = favicon ?: it.favicon,
                    blockedAdsCount = 0,
                    hasLoadedSuccessfully = false,
                    isPageTranslated = false,
                    showTranslateBar = false
                )
            }

            if (url != null) {
                triggerTabUpdatedEvent(tabId, url)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onPageStarted: ${e.message}", e)
        }
    }

    fun onPageCommitVisible(
        context: Context,
        view: WebView?,
        url: String?,
        tabId: String,
        isDesktopMode: Boolean,
        extensionSetup: (WebView, String) -> Unit,
        injectContentScripts: (WebView, String, String) -> Unit
    ) {
        if (view != null && url != null && !url.startsWith("swift://") && !url.startsWith("about:") && !url.startsWith("file://")) {
            try {
                extensionSetup(view, tabId)
                injectContentScripts(view, url, "document_start")

                SwiftCompatibilityEngine.injectCompatibilityLayer(view, url, context, isDesktopMode)

                com.swift.browser.notificationengine.api.NotificationEngineProvider.api.getJavascriptPolyfill(context, url) { polyfill ->
                    view.post { view.evaluateJavascript(polyfill, null) }
                }

                com.swift.browser.adblockengine.AdProtectionEngineApi.getInstance(context).onPageCommitVisible(tabId, view, url)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPageCommitVisible: ${e.message}", e)
            }
        }
    }

    fun onPageFinished(
        context: Context,
        view: WebView?,
        url: String?,
        tabId: String,
        getTab: (String) -> TabModel?,
        updateTabModel: (String, (TabModel) -> TabModel) -> Unit,
        flushCookies: () -> Unit,
        injectContentScripts: (WebView, String, String) -> Unit = { _, _, _ -> }
    ) {
        try {
            val currentTab = getTab(tabId)
            val isTabIncognito = currentTab?.isPrivate == true || currentTab?.isIncognito == true || (view as? BrowserWebView)?.isPrivate == true
            if (url != null) {
                val duration = com.swift.browser.analyticscore.AnalyticsCore.stopTimer("nav_$tabId")
                if (duration >= 0) {
                    com.swift.browser.analyticscore.AnalyticsCore.trackNavigation(
                        url = url,
                        loadDurationMs = duration,
                        context = if (isTabIncognito) com.swift.browser.analyticscore.AnalyticsContext.PRIVATE else com.swift.browser.analyticscore.AnalyticsContext.NORMAL
                    )
                }
            }

            if (view != null && url != null && !url.startsWith("swift://") && !url.startsWith("about:") && !url.startsWith("file://")) {
                injectContentScripts(view, url, "document_end")
                injectContentScripts(view, url, "document_idle")

                com.swift.browser.adblockengine.AdProtectionEngineApi.getInstance(context).onPageFinished(tabId, view, url)
            }

            try {
                flushCookies()
            } catch (e: Exception) {
                Log.e(TAG, "Cookie flush error: ${e.message}")
            }

            if (url != null) {
                if (view != null) {
                    com.swift.browser.desktopengine.webview.WebViewDesktopBridge.onPageFinished(tabId, url, view)
                }
                val host = try { Uri.parse(url).host } catch (e: Exception) { null }
                if (!host.isNullOrEmpty()) {
                    updateTabModel(tabId) { it.copy(hasLoadedSuccessfully = true) }
                } else {
                    updateTabModel(tabId) { it.copy(hasLoadedSuccessfully = true) }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onPageFinished: ${e.message}", e)
        }
    }

    // C. Security & SSL Handling
    fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: android.net.http.SslError?,
        loadErrorPage: (WebView?, String, String) -> Unit
    ) {
        try {
            val failingUrl = error?.url ?: ""
            val host = if (failingUrl.isNotEmpty()) {
                try { Uri.parse(failingUrl).host ?: "" } catch (e: Exception) { "" }
            } else ""

            val isFintech = host.endsWith("pay.google.com") ||
                    host.endsWith("payments.google.com") ||
                    host.endsWith("paypal.com") ||
                    host.endsWith("stripe.com")

            if (isFintech) {
                handler?.cancel()
                loadErrorPage(view, "ssl", failingUrl)
                return
            }

            val securityEngine = SwiftSecurityEngine
            if (host.isNotEmpty() && securityEngine.isSslWhitelisted(host)) {
                handler?.proceed()
                return
            }

            val webViewUrl = view?.url ?: ""
            val isMainFrame = if (failingUrl.isNotEmpty() && webViewUrl.isNotEmpty()) {
                val failingHost = try { Uri.parse(failingUrl).host } catch (e: Exception) { null }
                val webViewHost = try { Uri.parse(webViewUrl).host } catch (e: Exception) { null }
                failingHost != null && webViewHost != null && failingHost.equals(webViewHost, ignoreCase = true)
            } else {
                true
            }

            if (isMainFrame) {
                val sslState = SslWarningState(
                    showWarning = true,
                    url = failingUrl,
                    errorString = error?.toString() ?: "SSL Certificate Error",
                    handler = handler
                )
                securityEngine.setSslWarningState(sslState)
            } else {
                handler?.proceed()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onReceivedSslError: ${e.message}", e)
            handler?.cancel()
        }
    }

    // D. URL Navigation & Intent Routing
    fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
        isUASwitchPending: Boolean,
        lastManualLoadUrl: String,
        safeLoadUrl: (WebView?, String) -> Unit,
        tabId: String = ""
    ): Boolean {
        try {
            val isMainFrame = request?.isForMainFrame != false
            if (!isMainFrame) {
                return false
            }

            val url = request?.url?.toString() ?: return false

            if (isUASwitchPending) {
                return false
            }

            val domainCheck = try { Uri.parse(url).host } catch (e: Exception) { null }
            val lastDomain = try { Uri.parse(lastManualLoadUrl).host } catch (e: Exception) { null }
            if (domainCheck != null && lastDomain != null && domainCheck == lastDomain) {
                return false
            }

            if (url.startsWith("swift://retry")) {
                val queryUrl = request.url.getQueryParameter("url")
                if (!queryUrl.isNullOrBlank()) {
                    safeLoadUrl(view, queryUrl)
                } else {
                    safeLoadUrl(view, "swift://newtab")
                }
                return true
            }
            if (url.startsWith("swift://proceed_ssl")) {
                val queryUrl = request.url.getQueryParameter("url")
                if (!queryUrl.isNullOrBlank()) {
                    val host = try { Uri.parse(queryUrl).host } catch (e: Exception) { null }
                    if (host != null) {
                        SwiftSecurityEngine.whitelistDomain(host)
                    }
                    safeLoadUrl(view, queryUrl)
                } else {
                    safeLoadUrl(view, "swift://newtab")
                }
                return true
            }
            if (url.startsWith("javascript:", true)) {
                return true
            }
            if (url.startsWith("intent://", true)) {
                try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrBlank()) {
                        safeLoadUrl(view, fallbackUrl)
                    } else {
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        intent.setComponent(null)
                        intent.setSelector(null)
                        try {
                            view?.context?.startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            // If activity not found, check if it has a fallback URL or a data URI
                            val dataString = intent.dataString
                            if (!dataString.isNullOrBlank() && (dataString.startsWith("http://") || dataString.startsWith("https://"))) {
                                safeLoadUrl(view, dataString)
                            } else {
                                Log.e(TAG, "Activity not found for intent and no http fallback data.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed launching intent: ${e.message}")
                }
                return true
            }
            if (url.startsWith("tel:", true) || url.startsWith("mailto:", true) || url.startsWith("market:", true)) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    view?.context?.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed launching external action: ${e.message}")
                }
                return true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in shouldOverrideUrlLoading: ${e.message}", e)
        }
        return false
    }

    // E. Render Process Gone Handling
    fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
        tabId: String,
        removeWebView: (String) -> Unit,
        updateTabModel: (String, (TabModel) -> TabModel) -> Unit
    ): Boolean {
        try {
            Log.e(TAG, "WebView render process gone for tab $tabId")
            if (view != null) {
                view.post {
                    try {
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                        removeWebView(tabId)
                        view.destroy()
                        updateTabModel(tabId) { it.copy(isWebViewDestroyed = true) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error destroying gone WebView: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onRenderProcessGone: ${e.message}", e)
        }
        return true
    }

    // F. Console Logs
    fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let {
            val level = when (it.messageLevel()) {
                ConsoleMessage.MessageLevel.TIP -> LogLevel.INFO
                ConsoleMessage.MessageLevel.LOG -> LogLevel.LOG
                ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARNING
                ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                ConsoleMessage.MessageLevel.DEBUG -> LogLevel.DEBUG
                else -> LogLevel.LOG
            }
            val msgText = it.message() ?: ""
            val source = it.sourceId() ?: ""
            val line = it.lineNumber()

            Log.d("WebConsole", "[$level] $msgText ($source:$line)")
            InspectorEngine.instance.logConsole(
                level,
                "$msgText ($source:$line)"
            )
        }
        return true
    }

    // G. Permissions
    fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
        context: Context,
        tabId: String,
        url: String,
        isIncognito: Boolean
    ) {
        if (origin == null || callback == null) {
            callback?.invoke(origin, false, false)
            return
        }

        val permissionContext = com.swift.browser.permissionengine.PermissionRequestContext(
            requestId = java.util.UUID.randomUUID().toString(),
            tabId = tabId,
            origin = origin,
            pageUrl = url,
            frameId = "main",
            isMainFrame = true,
            isUserGesture = null,
            isIncognito = isIncognito,
            requestSource = "geolocation",
            timestamp = System.currentTimeMillis()
        )

        com.swift.browser.permissionengine.PermissionEngineApi.handleGeolocationRequest(
            context = permissionContext,
            androidContext = context,
            callback = callback
        )
    }

    fun onPermissionRequest(
        request: PermissionRequest?,
        context: Context,
        tabId: String,
        isIncognito: Boolean,
        url: String
    ) {
        if (request == null) return

        // Record media compatibility diagnostics at WebChromeClient callback boundary
        WebMediaCompatibilityEngine.logDiagnostics(context, url)

        val domain = try { Uri.parse(url).host ?: "" } catch(e: Exception) { "" }
        val originStr = request.origin?.toString() ?: domain
        val permissionContext = com.swift.browser.permissionengine.PermissionRequestContext(
            requestId = java.util.UUID.randomUUID().toString(),
            tabId = tabId,
            origin = originStr,
            pageUrl = url,
            frameId = "main",
            isMainFrame = true,
            isUserGesture = null, // UNKNOWN at raw WebView callback boundary
            isIncognito = isIncognito,
            requestSource = "website",
            timestamp = System.currentTimeMillis()
        )

        com.swift.browser.permissionengine.PermissionEngineApi.handleWebViewPermissionRequest(
            context = permissionContext,
            request = request,
            androidContext = context,
            onComplete = { outcome ->
                Log.i(TAG, "WebView request resolved with outcome: $outcome for requestId: ${permissionContext.requestId}")
            }
        )
    }

    fun onPermissionRequestCanceled(
        request: PermissionRequest?,
        clearPendingRequest: () -> Unit
    ) {
        if (request == null) {
            Log.e(TAG, "onPermissionRequestCanceled called with null request")
            return
        }
        Log.w(TAG, "Permission request canceled by web origin: ${request.origin}")
        com.swift.browser.permissionengine.PermissionEngineApi.handlePermissionRequestCanceled(request, clearPendingRequest)
    }

    // H. Window & Dialog Creation
    fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
        tabId: String,
        isIncognito: Boolean,
        createAndAddTabWebView: (Context, String) -> WebView?
    ): Boolean {
        if (view == null || resultMsg == null || resultMsg.obj !is WebView.WebViewTransport) {
            Log.w(TAG, "onCreateWindow rejected: invalid view or resultMsg transport")
            return false
        }
        val mainUrl = view.url ?: ""
        val contextLocal = view.context
        val requestId = java.util.UUID.randomUUID().toString()

        val permContext = com.swift.browser.permissionengine.PermissionRequestContext(
            requestId = requestId,
            tabId = tabId,
            origin = mainUrl,
            pageUrl = mainUrl,
            frameId = "main",
            isMainFrame = true,
            isUserGesture = isUserGesture,
            isIncognito = isIncognito,
            requestSource = "popup",
            timestamp = System.currentTimeMillis()
        )

        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        com.swift.browser.permissionengine.PermissionEngineApi.evaluatePopupRequest(
            context = permContext,
            isUserGesture = isUserGesture,
            androidContext = contextLocal
        ) { isAllowed ->
            mainHandler.post {
                if (isCompleted.compareAndSet(false, true)) {
                    if (isAllowed) {
                        val transport = resultMsg.obj as? WebView.WebViewTransport
                        if (transport != null) {
                            val newTabId = java.util.UUID.randomUUID().toString()
                            val newWebView = createAndAddTabWebView(contextLocal, newTabId)
                            if (newWebView != null) {
                                transport.webView = newWebView
                                resultMsg.sendToTarget()
                                Log.i(TAG, "Popup window CREATED successfully for tabId: $tabId, newTabId: $newTabId, origin: $mainUrl")
                            } else {
                                Log.e(TAG, "POPUP_TAB_CREATION_FAILED (null webview) for newTabId: $newTabId")
                            }
                        } else {
                            Log.e(TAG, "POPUP_TAB_CREATION_FAILED (null transport) for tabId: $tabId")
                        }
                    } else {
                        Log.i(TAG, "Popup window request BLOCKED / DENIED for tabId: $tabId, origin: $mainUrl")
                    }
                }
            }
        }

        return true
    }

    // I. File Chooser
    fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        tabId: String,
        isIncognito: Boolean,
        setFileChooserCallback: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit
    ): Boolean {
        if (webView == null || filePathCallback == null) {
            filePathCallback?.onReceiveValue(null)
            return false
        }
        val mainUrl = webView.url ?: ""
        val contextLocal = webView.context
        val requestId = java.util.UUID.randomUUID().toString()

        val permContext = com.swift.browser.permissionengine.PermissionRequestContext(
            requestId = requestId,
            tabId = tabId,
            origin = mainUrl,
            pageUrl = mainUrl,
            frameId = "main",
            isMainFrame = true,
            isUserGesture = null,
            isIncognito = isIncognito,
            requestSource = "file_chooser",
            timestamp = System.currentTimeMillis()
        )

        val atomicCallback = object : ValueCallback<Array<Uri>> {
            private val isInvoked = java.util.concurrent.atomic.AtomicBoolean(false)
            override fun onReceiveValue(value: Array<Uri>?) {
                if (isInvoked.compareAndSet(false, true)) {
                    filePathCallback.onReceiveValue(value)
                }
            }
        }

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        com.swift.browser.permissionengine.PermissionEngineApi.evaluateFileChooserRequest(
            context = permContext,
            fileChooserParams = fileChooserParams,
            androidContext = contextLocal
        ) { isAllowed ->
            mainHandler.post {
                if (isAllowed) {
                    setFileChooserCallback(atomicCallback, fileChooserParams)
                } else {
                    Log.w(TAG, "File chooser blocked or denied for tab: $tabId, origin: $mainUrl")
                    atomicCallback.onReceiveValue(null)
                }
            }
        }

        return true
    }
}
