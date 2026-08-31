package com.swift.browser.extensionengine

import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

object ExtensionPageLoader {

    private const val TAG = "EXT_WEBVIEW"

    /**
     * Loads a page specified by an ExtensionPageTarget instance.
     */
    fun loadExtensionPage(
        webView: WebView,
        target: ExtensionPageTarget,
        api: ExtensionEngineApi,
        onPageFinishedCallback: ((String) -> Unit)? = null
    ) {
        loadExtensionPage(
            webView = webView,
            extensionId = target.extensionId,
            pageUrl = target.fullUrl,
            api = api,
            isPrivate = target.isPrivate,
            onPageFinishedCallback = onPageFinishedCallback
        )
    }

    /**
     * Loads a chrome-extension:// surface page safely using loadDataWithBaseURL
     * to eliminate net::ERR_UNKNOWN_URL_SCHEME while preserving full origin identity and API bindings.
     */
    fun loadExtensionPage(
        webView: WebView,
        extensionId: String,
        pageUrl: String,
        api: ExtensionEngineApi,
        isPrivate: Boolean = false,
        onPageFinishedCallback: ((String) -> Unit)? = null
    ) {
        configureExtensionWebView(webView, extensionId, api, isPrivate, onPageFinishedCallback)

        val context = webView.context.applicationContext
        val interceptedResponse = ExtensionDirectoryResolver.handleExtensionRequest(
            context = context,
            urlStr = pageUrl,
            isPrivate = isPrivate
        )

        if (interceptedResponse != null && interceptedResponse.data != null) {
            try {
                val bytes = interceptedResponse.data.readBytes()
                val encoding = interceptedResponse.encoding ?: "UTF-8"
                val htmlStr = String(bytes, charset(encoding))

                val baseUrl = if (pageUrl.contains("/")) {
                    pageUrl.substringBeforeLast("/") + "/"
                } else {
                    "chrome-extension://$extensionId/"
                }

                Log.d(TAG, "[EXT_PAGE] Loading extension page via loadDataWithBaseURL baseUrl=$baseUrl pageUrl=$pageUrl")
                webView.loadDataWithBaseURL(baseUrl, htmlStr, "text/html", encoding, pageUrl)
            } catch (e: Exception) {
                Log.e(TAG, "[EXT_PAGE] Failed to read HTML stream for pageUrl=$pageUrl", e)
                val errorHtml = "<html><body style='font-family:sans-serif;padding:16px;'><h3>Extension Page Read Error</h3><p>$pageUrl</p></body></html>"
                val baseUrl = "chrome-extension://$extensionId/"
                webView.loadDataWithBaseURL(baseUrl, errorHtml, "text/html", "UTF-8", pageUrl)
            }
        } else {
            Log.w(TAG, "[EXT_PAGE] Resource not found for pageUrl=$pageUrl")
            val errorHtml = "<html><body style='font-family:sans-serif;padding:16px;'><h3>Extension Page Not Found</h3><p>$pageUrl</p></body></html>"
            val baseUrl = "chrome-extension://$extensionId/"
            webView.loadDataWithBaseURL(baseUrl, errorHtml, "text/html", "UTF-8", pageUrl)
        }
    }

    /**
     * Configures a WebView specifically for rendering Chrome extension surface pages
     * (popup, side panel, options page, devtools, url overrides).
     */
    fun configureExtensionWebView(
        webView: WebView,
        extensionId: String,
        api: ExtensionEngineApi,
        isPrivate: Boolean = false,
        onPageFinishedCallback: ((String) -> Unit)? = null
    ) {
        val context = webView.context.applicationContext

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            try {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            } catch (e: Exception) {
                // Ignore API level differences
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url?.toString() ?: return false
                if (targetUrl.startsWith("chrome-extension://") || targetUrl.startsWith("swift-extension://")) {
                    if (view != null) {
                        Log.d(TAG, "[EXT_WEBVIEW] Intercepting sub-navigation to $targetUrl")
                        loadExtensionPage(view, extensionId, targetUrl, api, isPrivate, onPageFinishedCallback)
                        return true
                    }
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlStr = request?.url?.toString() ?: return null

                if (urlStr.startsWith("chrome-extension://") || urlStr.startsWith("swift-extension://")) {
                    val bridge = view?.tag as? RuntimeBridge
                    val reqPrivate = bridge?.isPrivate ?: isPrivate

                    val interceptedResponse = ExtensionDirectoryResolver.handleExtensionRequest(
                        context = context,
                        urlStr = urlStr,
                        isPrivate = reqPrivate
                    )
                    if (interceptedResponse != null) {
                        return interceptedResponse
                    } else {
                        Log.w(TAG, "[EXT_RESOURCE_DENY] Extension resource request denied or missing: $urlStr")
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null) {
                    api.setupWebView(view, extensionId)
                }
                if (url != null) {
                    onPageFinishedCallback?.invoke(url)
                }
            }
        }

        api.setupWebView(webView, extensionId)
    }
}
