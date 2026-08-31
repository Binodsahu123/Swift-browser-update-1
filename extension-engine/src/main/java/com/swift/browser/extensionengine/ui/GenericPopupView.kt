package com.swift.browser.extensionengine.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.ExtensionPageLoader

@Composable
fun GenericPopupView(
    extensionId: String,
    popupUrl: String,
    api: ExtensionEngineApi,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                ExtensionPageLoader.loadExtensionPage(
                    webView = this,
                    extensionId = extensionId,
                    pageUrl = popupUrl,
                    api = api
                )
            }
        },
        update = { webView ->
            api.setupWebView(webView, extensionId)
        },
        modifier = modifier.fillMaxSize()
    )
}
