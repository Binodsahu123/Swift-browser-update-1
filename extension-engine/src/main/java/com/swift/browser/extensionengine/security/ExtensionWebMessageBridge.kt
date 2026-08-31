package com.swift.browser.extensionengine.security

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.net.Uri

/**
 * Modern WebMessageListener bridge handler with origin isolation.
 */
object ExtensionWebMessageBridge {

    enum class BridgeFeatureStatus {
        SUPPORTED,
        UNSUPPORTED_BY_WEBVIEW
    }

    fun isWebMessageListenerSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    fun setupWebMessageListener(
        webView: WebView,
        listenerName: String,
        allowedOrigins: Set<String>,
        onMessageReceived: (message: String, sourceOrigin: String, isMainFrame: Boolean, replyProxy: JavaScriptReplyProxy?) -> Unit
    ): BridgeFeatureStatus {
        if (!isWebMessageListenerSupported()) {
            return BridgeFeatureStatus.UNSUPPORTED_BY_WEBVIEW
        }

        try {
            val rules = if (allowedOrigins.isEmpty()) setOf("*") else allowedOrigins
            WebViewCompat.addWebMessageListener(
                webView,
                listenerName,
                rules,
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy
                    ) {
                        val payload = message.data ?: ""
                        val originStr = sourceOrigin.toString()
                        onMessageReceived(payload, originStr, isMainFrame, replyProxy)
                    }
                }
            )
            return BridgeFeatureStatus.SUPPORTED
        } catch (e: Exception) {
            return BridgeFeatureStatus.UNSUPPORTED_BY_WEBVIEW
        }
    }
}
