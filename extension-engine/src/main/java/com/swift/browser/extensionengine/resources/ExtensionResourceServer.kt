package com.swift.browser.extensionengine.resources

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.swift.browser.extensionengine.ExtensionError
import com.swift.browser.extensionengine.ExtensionRegistry
import com.swift.browser.extensionengine.PermissionManager
import com.swift.browser.extensionengine.origin.ExtensionUrl
import com.swift.browser.extensionengine.security.ExtensionPageType
import java.io.ByteArrayInputStream

/**
 * Production-Grade Resource Server for WebView Interception.
 * Bridges ExtensionResourceResolver with Android WebView's WebResourceResponse API.
 */
class ExtensionResourceServer(
    private val context: Context,
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager? = null
) {
    private val resolver = ExtensionResourceResolver(context, registry, permissionManager)

    fun handleRequest(
        request: WebResourceRequest?,
        isPrivate: Boolean = false,
        pageType: ExtensionPageType = ExtensionPageType.EXTENSION_PAGE
    ): WebResourceResponse? {
        val urlStr = request?.url?.toString() ?: return null
        val initiatorStr = request?.requestHeaders?.get("Origin")
            ?: request?.requestHeaders?.get("Referer")

        return handleUrlRequest(
            urlStr = urlStr,
            initiatorUrlStr = initiatorStr,
            isPrivate = isPrivate,
            pageType = pageType
        )
    }

    fun handleUrlRequest(
        urlStr: String?,
        initiatorUrlStr: String? = null,
        isPrivate: Boolean = false,
        pageType: ExtensionPageType = ExtensionPageType.EXTENSION_PAGE
    ): WebResourceResponse? {
        if (urlStr.isNullOrBlank()) return null
        if (!ExtensionUrl.isExtensionUrl(urlStr)) return null

        return try {
            val result = resolver.resolveResource(
                requestUrlStr = urlStr,
                initiatorUrlStr = initiatorUrlStr,
                isPrivate = isPrivate,
                pageType = pageType
            )

            val stream = result.inputStreamProvider?.invoke() ?: return createErrorResponse(
                statusCode = 404,
                reason = "Not Found",
                message = "Resource content missing"
            )

            WebResourceResponse(
                result.mimeType,
                result.encoding,
                result.statusCode,
                result.reasonPhrase,
                result.headers,
                stream
            )
        } catch (e: ExtensionError.SecurityError) {
            when (e) {
                is ExtensionError.SecurityError.ExtensionNotFound,
                is ExtensionError.SecurityError.ResourceNotAccessible -> {
                    createErrorResponse(404, "Not Found", "Resource not found or inaccessible")
                }
                is ExtensionError.SecurityError.AccessDenied,
                is ExtensionError.SecurityError.CrossExtensionAccessDenied,
                is ExtensionError.SecurityError.PathTraversalDetected,
                is ExtensionError.SecurityError.InvalidExtensionOrigin -> {
                    createErrorResponse(403, "Forbidden", "Extension resource access denied")
                }
                else -> {
                    createErrorResponse(400, "Bad Request", "Invalid extension request")
                }
            }
        } catch (e: Exception) {
            createErrorResponse(500, "Internal Error", "Failed to resolve extension resource")
        }
    }

    private fun createErrorResponse(
        statusCode: Int,
        reason: String,
        message: String
    ): WebResourceResponse {
        val headers = mapOf(
            "X-Content-Type-Options" to "nosniff",
            "Cache-Control" to "no-cache, no-store, must-revalidate"
        )
        val stream = ByteArrayInputStream(message.toByteArray(Charsets.UTF_8))
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            statusCode,
            reason,
            headers,
            stream
        )
    }
}
