package com.swift.browser.webstudio.preview

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PreviewEngine(url: String, htmlContent: String?) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isDesktop by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(Color(0xFFE2E8F0)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                hasError = false
                webViewRef?.reload() 
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = { 
                isDesktop = !isDesktop 
                val context = webViewRef?.context
                val host = try { android.net.Uri.parse(url).host.orEmpty() } catch (_: Exception) { "" }
                webViewRef?.settings?.userAgentString = if (isDesktop) {
                    com.swift.browser.desktopengine.useragent.UserAgentManager.getDesktopUserAgent(host, context)
                } else {
                    com.swift.browser.desktopengine.useragent.UserAgentManager.getMobileUserAgent(context)
                }
                webViewRef?.reload()
            }) {
                Icon(if (isDesktop) Icons.Default.DesktopMac else Icons.Default.PhoneAndroid, contentDescription = "Toggle Desktop View")
            }
        }
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                hasError = false
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                isLoading = false
                                hasError = true
                            }
                        }
                        webChromeClient = WebChromeClient()
                    }.also { webViewRef = it }
                },
                update = { webView ->
                    if (url.isNotEmpty()) {
                        webView.loadUrl(url)
                    } else if (!htmlContent.isNullOrEmpty()) {
                        webView.loadDataWithBaseURL("http://localhost", htmlContent, "text/html", "utf-8", null)
                    } else {
                        webView.loadDataWithBaseURL("http://localhost", "<div style='color:gray;text-align:center;margin-top:20px;'>Nothing to preview</div>", "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (hasError) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Failed to load preview.", color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { hasError = false; webViewRef?.reload() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
