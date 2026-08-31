package com.swift.browser.browserengine

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.swift.browser.extensionengine.ExtensionNetworkRequestContext
import com.swift.browser.extensionengine.WebRequestInterceptResult
import com.swift.browser.securityengine.SecurityEngineProvider
import java.io.ByteArrayInputStream

/**
 * Single authoritative network interception coordinator for Orion Browser.
 * Enforces security policy, adblock policy, and extension DNR / WebRequest rules.
 * NEVER proxies normal requests through OkHttp.
 */
object WebResourceInterceptionCoordinator {
    private const val TAG = "WebResourceInterception"

    fun shouldInterceptRequest(
        context: Context,
        view: WebView?,
        request: WebResourceRequest?,
        currentDocUrl: String?,
        onAdBlocked: () -> Unit
    ): WebResourceResponse? {
        return try {
            val urlStr = request?.url?.toString() ?: return null

            // 1. Extension Resolver (chrome-extension://, swift-extension://)
            if (urlStr.startsWith("chrome-extension://") || urlStr.startsWith("swift-extension://")) {
                val interceptRes = com.swift.browser.extensionengine.ExtensionDirectoryResolver.handleExtensionRequest(context, urlStr)
                if (interceptRes != null) return interceptRes
            }

            // 2. Subresource Security Policy Check
            if (request != null && !request.isForMainFrame) {
                val isSafe = SecurityEngineProvider.api.isSubresourceSafe(urlStr, currentDocUrl)
                if (!isSafe) {
                    Log.w(TAG, "Subresource blocked by Security Engine: $urlStr")
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by Security Policy", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }

            // 3. AdBlock Policy
            var adblockRes: WebResourceResponse? = null
            if (request != null && !request.isForMainFrame) {
                adblockRes = com.swift.browser.adblockengine.AdProtectionEngineApi.getInstance(context).shouldInterceptRequest(context, urlStr, currentDocUrl)
            }

            if (adblockRes != null) {
                onAdBlocked()
                return adblockRes
            }

            // 4. Extension DNR & WebRequest Interception
            if (request != null) {
                try {
                    val contextApp = context.applicationContext
                    val engineApi = com.swift.browser.extensionengine.ExtensionEngineApi.getInstance(contextApp)
                    val engineImpl = engineApi.extensionManager.engine as? com.swift.browser.extensionengine.ExtensionEngineImpl
                    if (engineImpl != null) {
                        val bridge = view?.tag as? com.swift.browser.extensionengine.RuntimeBridge
                        val tabIdStr = bridge?.tabId ?: "-1"
                        val tabId = tabIdStr.toIntOrNull() ?: -1
                        val isPrivate = bridge?.isPrivate ?: false
                        val privateSessionId = bridge?.privateSessionId

                        val reqHeaders = request.requestHeaders ?: emptyMap()
                        val reqId = engineImpl.webRequestAdapter.idMapper.getOrCreateRequestId(urlStr)

                        val networkContext = ExtensionNetworkRequestContext(
                            requestId = reqId,
                            url = urlStr,
                            method = request.method ?: "GET",
                            resourceType = guessResourceType(request),
                            tabId = tabId,
                            isPrivate = isPrivate,
                            privateSessionId = privateSessionId,
                            requestHeaders = reqHeaders,
                            isForMainFrame = request.isForMainFrame,
                            initiator = currentDocUrl
                        )

                        val webResult = engineImpl.webRequestAdapter.interceptRequest(networkContext)
                        when (webResult) {
                            is WebRequestInterceptResult.Blocked -> {
                                Log.i(TAG, "Blocked by extension policy: $urlStr")
                                onAdBlocked()
                                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by Extension Policy", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            is WebRequestInterceptResult.Redirect -> {
                                Log.i(TAG, "Redirect requested by extension policy: $urlStr -> ${webResult.redirectUrl}")
                                // Handle scheme upgrades (http -> https) or safe redirects
                                if (webResult.redirectUrl.startsWith("https://") && urlStr.startsWith("http://")) {
                                    val redirectedResponse = handleSafeRedirectRequest(webResult.redirectUrl, request)
                                    if (redirectedResponse != null) return redirectedResponse
                                }
                            }
                            is WebRequestInterceptResult.ModifyHeaders -> {
                                // Log modifyHeaders observation; do not force proxy normal requests
                                Log.d(TAG, "ModifyHeaders requested for: $urlStr")
                            }
                            else -> { /* Continue to native WebView request processing */ }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error evaluating extension network policy for: $urlStr", e)
                }
            }

            // 5. Diagnostics & Request Sniffing (Observation Only)
            if (request != null) {
                val method = request.method ?: "GET"
                val headers = request.requestHeaders ?: emptyMap()
                com.swift.browser.adblockengine.RequestInterceptorEngine.interceptAndRecord(urlStr, method, headers, context)
            }

            // Return null so native Android WebView handles the request directly
            return null
        } catch (e: Throwable) {
            Log.e(TAG, "Error in interception coordinator: ${e.message}", e)
            null
        }
    }

    private fun guessResourceType(request: WebResourceRequest?): String {
        if (request == null) return "other"
        if (request.isForMainFrame) return "main_frame"
        val path = request.url?.path?.lowercase() ?: ""
        return when {
            path.endsWith(".js") -> "script"
            path.endsWith(".css") -> "stylesheet"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                    path.endsWith(".ico") -> "image"
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                    path.endsWith(".otf") -> "font"
            path.endsWith(".mp4") || path.endsWith(".mp3") || path.endsWith(".webm") ||
                    path.endsWith(".ogg") -> "media"
            path.endsWith(".html") || path.endsWith(".htm") -> "sub_frame"
            else -> {
                val accept = request.requestHeaders?.get("Accept")?.lowercase() ?: ""
                when {
                    accept.contains("text/html") -> "sub_frame"
                    accept.contains("text/css") -> "stylesheet"
                    accept.contains("javascript") || accept.contains("application/x-javascript") -> "script"
                    accept.contains("image/") -> "image"
                    else -> "other"
                }
            }
        }
    }

    private fun handleSafeRedirectRequest(redirectUrl: String, originalRequest: WebResourceRequest?): WebResourceResponse? {
        return try {
            val builder = okhttp3.Request.Builder().url(redirectUrl)
            if (originalRequest != null) {
                val method = originalRequest.method ?: "GET"
                builder.method(method, null)
                originalRequest.requestHeaders?.forEach { (k, v) ->
                    builder.addHeader(k, v)
                }
            }
            val response = com.swift.browser.networkcore.NetworkCore.okHttpClient.newCall(builder.build()).execute()
            val mimeType = response.header("Content-Type")?.substringBefore(";") ?: "text/html"
            val encoding = response.header("Content-Type")?.substringAfter("charset=", "UTF-8") ?: "UTF-8"
            val responseHeaders = mutableMapOf<String, String>()
            response.headers.forEach { pair ->
                responseHeaders[pair.first] = pair.second
            }
            val responseBody = response.body
            if (responseBody != null) {
                WebResourceResponse(
                    mimeType,
                    encoding,
                    response.code,
                    response.message.ifBlank { "OK" },
                    responseHeaders,
                    responseBody.byteStream()
                )
            } else {
                WebResourceResponse(
                    mimeType,
                    encoding,
                    response.code,
                    response.message.ifBlank { "OK" },
                    responseHeaders,
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Safe redirection fetch failed to $redirectUrl", e)
            null
        }
    }
}
