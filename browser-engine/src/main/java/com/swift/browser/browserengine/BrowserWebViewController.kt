package com.swift.browser.browserengine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.swift.browser.tabengine.model.TabModel

/**
 * Controller and factory responsible for instantiating, configuring, attaching lifecycle callbacks,
 * and maintaining WebViews for tabs in the Swift Browser Engine.
 */
class BrowserWebViewController(
    private val context: Context,
    private val callbackEngine: WebViewCoreCallbackEngine = WebViewCoreCallbackEngine.getInstance()
) {

    data class WebViewConfiguration(
        val isJavaScriptEnabled: Boolean = true,
        val isHardwareAccelerationEnabled: Boolean = true,
        val isDesktopMode: Boolean = false,
        val isIncognito: Boolean = false,
        val isPrivate: Boolean = isIncognito,
        val privateSessionId: String? = null,
        val profileName: String? = null
    )

    interface WebViewUiCallbacks {
        fun onContextMenuRequested(url: String, isImage: Boolean, isImageLink: Boolean) {}
        fun onDownloadRequested(url: String, userAgent: String, contentDisposition: String, mimetype: String, contentLength: Long) {}
        fun onBlockedAdCountIncremented(tabId: String) {}
        fun onPageStarted(tabId: String, url: String) {}
        fun onPageFinished(tabId: String, url: String, title: String) {}
        fun onProgressChanged(tabId: String, progress: Int) {}
        fun onTitleReceived(tabId: String, title: String) {}
        fun onFaviconReceived(tabId: String, favicon: Bitmap) {}
        fun onErrorPageRequested(view: WebView?, errorType: String, failingUrl: String) {}
        fun onShowFileChooser(filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?) {}
        fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {}
        fun onHideCustomView() {}
        fun onNewTabRequested(url: String, isIncognito: Boolean): WebView? = null
        fun onPendingGeolocationPrompt(origin: String, callback: GeolocationPermissions.Callback) {}
        fun onClearPermissionRequest() {}
        fun getDomain(url: String): String = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        fun getPermissionStatus(host: String, permission: String): String = "Ask"
        fun logPermissionAction(host: String, permission: String, action: String) {}
    }

    fun createAndConfigureWebView(
        tabId: String,
        config: WebViewConfiguration,
        getTab: (String) -> TabModel?,
        updateTabModel: (String, (TabModel) -> TabModel) -> Unit,
        triggerTabUpdatedEvent: (String, String) -> Unit,
        extensionSetup: (WebView, String) -> Unit,
        injectContentScripts: (WebView, String, String) -> Unit,
        flushCookies: () -> Unit,
        permissionEngine: Any?,
        uiCallbacks: WebViewUiCallbacks
    ): BrowserWebView {
        val tabModel = getTab(tabId)
        val isPrivateMode = config.isPrivate || config.isIncognito || tabModel?.isPrivate == true || tabModel?.isIncognito == true

        val webView = BrowserWebView(context, tabId).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            this.isPrivate = isPrivateMode

            if (isPrivateMode) {
                val privateModeEngine = com.swift.browser.privatemode.PrivateModeEngineProvider.getEngine(context)
                var pSessionId = config.privateSessionId ?: tabModel?.privateSessionId ?: privateModeEngine.state.value.tabToSessionMap[tabId]
                if (pSessionId == null || privateModeEngine.getSession(pSessionId) == null) {
                    val activeSession = privateModeEngine.getActivePrivateSession() ?: privateModeEngine.openSession()
                    pSessionId = activeSession.sessionId
                }
                privateModeEngine.attachTab(pSessionId, tabId)
                val session = privateModeEngine.getSession(pSessionId)
                val pName = config.profileName ?: session?.profileName ?: "private_profile_$pSessionId"

                this.privateSessionId = pSessionId
                this.profileName = pName

                // FIRST: Set private profile & WebViewCompat before settings & bridges
                privateModeEngine.configureWebViewForPrivateMode(this, pSessionId)
            }

            // THEN: Apply WebSettings
            BrowserWebViewSettings.applySettings(
                webView = this,
                jsEnabled = config.isJavaScriptEnabled,
                hwEnabled = config.isHardwareAccelerationEnabled,
                isDesktop = config.isDesktopMode,
                isIncognito = isPrivateMode
            )

            // Register Web Speech Recognition Bridge
            val speechBridgeInstance = WebSpeechRecognitionBridge(
                webView = this,
                context = context,
                tabId = tabId,
                isIncognito = isPrivateMode
            )
            this.speechBridge = speechBridgeInstance
            addJavascriptInterface(
                speechBridgeInstance,
                WebSpeechRecognitionBridge.INTERFACE_NAME
            )

            // Register Web Notification Bridge
            val notificationBridgeInstance = com.swift.browser.notificationengine.AndroidNotificationBridge(
                context = context,
                webView = this,
                tabId = tabId,
                isIncognito = isPrivateMode
            )
            addJavascriptInterface(
                notificationBridgeInstance,
                com.swift.browser.notificationengine.AndroidNotificationBridge.INTERFACE_NAME
            )

            // Register Web Clipboard Bridge
            val clipboardBridgeInstance = WebClipboardBridge(
                webView = this,
                context = context,
                tabId = tabId,
                isIncognito = isPrivateMode
            )
            addJavascriptInterface(
                clipboardBridgeInstance,
                WebClipboardBridge.INTERFACE_NAME
            )

            // Register Web Screen Capture Bridge
            val screenCaptureBridgeInstance = com.swift.browser.browserengine.screencapture.WebScreenCaptureBridge(
                webView = this,
                context = context,
                tabId = tabId,
                isIncognito = isPrivateMode
            )
            addJavascriptInterface(
                screenCaptureBridgeInstance,
                com.swift.browser.browserengine.screencapture.WebScreenCaptureBridge.INTERFACE_NAME
            )

            // Initialize WebRTC Runtime Manager
            com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.initialize(context)

            // Register WebRTC Bridge
            val webRtcBridgeInstance = com.swift.browser.browserengine.webrtc.WebRtcBridge(
                webView = this,
                tabId = tabId
            )
            addJavascriptInterface(
                webRtcBridgeInstance,
                com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.INTERFACE_NAME
            )

            // Long Click Listener for Context Menu
            setOnLongClickListener {
                val result = hitTestResult
                val type = result.type
                val extra = result.extra
                if (extra != null) {
                    when (type) {
                        WebView.HitTestResult.SRC_ANCHOR_TYPE,
                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            uiCallbacks.onContextMenuRequested(
                                url = extra,
                                isImage = false,
                                isImageLink = (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
                            )
                            true
                        }
                        WebView.HitTestResult.IMAGE_TYPE -> {
                            uiCallbacks.onContextMenuRequested(url = extra, isImage = true, isImageLink = false)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }

            // Download Listener
            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                uiCallbacks.onDownloadRequested(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition ?: "",
                    mimetype = mimetype,
                    contentLength = contentLength
                )
            }

            // Core WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val result = callbackEngine.shouldInterceptRequest(
                        context = context,
                        view = view,
                        request = request,
                        currentDocUrl = getTab(tabId)?.url,
                        onAdBlocked = { uiCallbacks.onBlockedAdCountIncremented(tabId) }
                    )
                    return result ?: super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (view != null && url != null) {
                        try {
                            com.swift.browser.extensionengine.ExtensionEngineApi.getInstance(context).onPageStarted(view, url)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    callbackEngine.onPageStarted(
                        context = context,
                        view = view,
                        url = url,
                        favicon = favicon,
                        tabId = tabId,
                        getTab = getTab,
                        updateTabModel = updateTabModel,
                        triggerTabUpdatedEvent = triggerTabUpdatedEvent
                    )
                    if (url != null) {
                        (view as? BrowserWebView)?.speechBridge?.onPageStarted(url)
                        uiCallbacks.onPageStarted(tabId, url)
                        com.swift.browser.browserengine.webrtc.GenericWebMediaCompatibilityEngine.invalidateSession(tabId)
                    }
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    val isDesktop = try { com.swift.browser.desktopengine.api.DesktopEngineProvider.api.isDesktopMode(android.net.Uri.parse(url).host ?: "") } catch(e: Exception) { false }
                    callbackEngine.onPageCommitVisible(
                        context = context,
                        view = view,
                        url = url,
                        tabId = tabId,
                        isDesktopMode = isDesktop,
                        extensionSetup = extensionSetup,
                        injectContentScripts = injectContentScripts
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    callbackEngine.onPageFinished(
                        context = context,
                        view = view,
                        url = url,
                        tabId = tabId,
                        getTab = getTab,
                        updateTabModel = updateTabModel,
                        flushCookies = flushCookies,
                        injectContentScripts = injectContentScripts
                    )
                    if (url != null) {
                        uiCallbacks.onPageFinished(tabId, url, view?.title ?: "")
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (url != null && !url.startsWith("swift://") && !url.startsWith("about:") && !url.startsWith("file://")) {
                        updateTabModel(tabId) { it.copy(url = url) }
                        uiCallbacks.onPageStarted(tabId, url) // Update URL bar
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    callbackEngine.onReceivedSslError(
                        view = view,
                        handler = handler,
                        error = error,
                        loadErrorPage = { v, type, failingUrl ->
                            uiCallbacks.onErrorPageRequested(v, type, failingUrl)
                        }
                    )
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return callbackEngine.shouldOverrideUrlLoading(
                        view = view,
                        request = request,
                        isUASwitchPending = false,
                        lastManualLoadUrl = getTab(tabId)?.url ?: "",
                        safeLoadUrl = { v, loadUrl ->
                            BrowserNavigationApi.navigate(
                                NavigationRequest(
                                    tabId = tabId,
                                    url = loadUrl,
                                    source = NavigationSource.RECOVERY,
                                    applyDesktopPolicy = false,
                                    webView = v
                                )
                            )
                        },
                        tabId = tabId
                    )
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    val isMainFrame = request?.isForMainFrame ?: false
                    val currentTab = getTab(tabId)
                    if (isMainFrame && currentTab?.hasLoadedSuccessfully != true) {
                        val failingUrl = request?.url?.toString() ?: ""
                        uiCallbacks.onErrorPageRequested(view, "offline", failingUrl)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val isMainFrame = request?.isForMainFrame ?: false
                    if (isMainFrame && errorResponse?.statusCode == 404) {
                        val failingUrl = request?.url?.toString() ?: ""
                        uiCallbacks.onErrorPageRequested(view, "404", failingUrl)
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    return callbackEngine.onRenderProcessGone(
                        view = view,
                        detail = detail,
                        tabId = tabId,
                        removeWebView = { /* Handled via tab model update */ },
                        updateTabModel = updateTabModel
                    )
                }
            }

            // Core WebChromeClient
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    return callbackEngine.onConsoleMessage(consoleMessage)
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    updateTabModel(tabId) {
                        it.copy(progress = newProgress, isLoading = newProgress < 100)
                    }
                    uiCallbacks.onProgressChanged(tabId, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, titleStr: String?) {
                    super.onReceivedTitle(view, titleStr)
                    val currentUrl = view?.url ?: ""
                    val finalTitle = if (currentUrl == "swift://newtab" || currentUrl == "swift://newtab-incognito") {
                        val isTabIncognito = getTab(tabId)?.isIncognito == true
                        if (isTabIncognito) "Incognito Tab" else "New Tab"
                    } else {
                        (titleStr ?: "").ifBlank { currentUrl }
                    }
                    updateTabModel(tabId) { it.copy(title = finalTitle) }
                    uiCallbacks.onTitleReceived(tabId, finalTitle)
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    if (icon != null) {
                        updateTabModel(tabId) { it.copy(favicon = icon) }
                        uiCallbacks.onFaviconReceived(tabId, icon)
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    val tabModel = getTab(tabId)
                    val isIncognito = tabModel?.isIncognito ?: false
                    val tabUrl = tabModel?.url ?: ""
                    callbackEngine.onGeolocationPermissionsShowPrompt(
                        origin = origin,
                        callback = callback,
                        context = context,
                        tabId = tabId,
                        url = tabUrl,
                        isIncognito = isIncognito
                    )
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    callbackEngine.onPermissionRequest(
                        request = request,
                        context = context,
                        tabId = tabId,
                        isIncognito = getTab(tabId)?.isIncognito == true,
                        url = getTab(tabId)?.url ?: ""
                    )
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                    callbackEngine.onPermissionRequestCanceled(
                        request = request,
                        clearPendingRequest = { uiCallbacks.onClearPermissionRequest() }
                    )
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val isTabIncognito = getTab(tabId)?.isIncognito == true
                    return callbackEngine.onCreateWindow(
                        view = view,
                        isDialog = isDialog,
                        isUserGesture = isUserGesture,
                        resultMsg = resultMsg,
                        tabId = tabId,
                        isIncognito = isTabIncognito,
                        createAndAddTabWebView = { ctx, newTabId ->
                            uiCallbacks.onNewTabRequested("about:blank", isTabIncognito)
                        }
                    )
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    val isTabIncognito = getTab(tabId)?.isIncognito == true
                    return callbackEngine.onShowFileChooser(
                        webView = webView,
                        filePathCallback = filePathCallback,
                        fileChooserParams = fileChooserParams,
                        tabId = tabId,
                        isIncognito = isTabIncognito,
                        setFileChooserCallback = { cb, params ->
                            uiCallbacks.onShowFileChooser(cb, params)
                        }
                    )
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        val isIncognito = getTab(tabId)?.isIncognito == true
                        val currentUrl = getTab(tabId)?.url ?: ""
                        val params = com.swift.browser.permissionengine.FullscreenRequestParams(
                            origin = currentUrl,
                            tabId = tabId,
                            userGesture = null,
                            isIncognito = isIncognito
                        )
                        com.swift.browser.permissionengine.PermissionEngineApi.handleFullscreenRequest(context, params) { isAllowed ->
                            if (isAllowed) {
                                uiCallbacks.onShowCustomView(view, callback)
                            } else {
                                try { callback.onCustomViewHidden() } catch (_: Exception) {}
                            }
                        }
                    }
                }

                override fun onHideCustomView() {
                    uiCallbacks.onHideCustomView()
                }
            }
        }

        return webView
    }
}
