package com.swift.browser.extensionengine.resources

import android.content.Context
import com.swift.browser.extensionengine.ExtensionDirectoryResolver
import com.swift.browser.extensionengine.ExtensionError
import com.swift.browser.extensionengine.ExtensionRegistry
import com.swift.browser.extensionengine.PathSanitizer
import com.swift.browser.extensionengine.PermissionManager
import com.swift.browser.extensionengine.origin.ExtensionUrl
import com.swift.browser.extensionengine.security.ExtensionCspPolicy
import com.swift.browser.extensionengine.security.ExtensionPageType
import java.io.File
import java.io.InputStream

data class ExtensionResourceResult(
    val extensionId: String,
    val resourcePath: String,
    val mimeType: String,
    val encoding: String?,
    val file: File?,
    val inputStreamProvider: (() -> InputStream)?,
    val headers: Map<String, String>,
    val statusCode: Int = 200,
    val reasonPhrase: String = "OK"
)

/**
 * Production-Grade Extension Resource Resolver.
 * Performs canonical path containment verification, origin isolation, access policy enforcement,
 * and header generation for extension resource loading.
 */
class ExtensionResourceResolver(
    private val context: Context,
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager? = null
) {

    fun resolveResource(
        requestUrlStr: String,
        initiatorUrlStr: String? = null,
        isPrivate: Boolean = false,
        pageType: ExtensionPageType = ExtensionPageType.EXTENSION_PAGE
    ): ExtensionResourceResult {
        // 1. Parse Extension URL
        val urlResult = ExtensionUrl.parseExtensionUrl(requestUrlStr)
            ?: throw ExtensionError.SecurityError.InvalidExtensionOrigin(
                requestUrlStr,
                "Failed to parse valid chrome-extension:// URL"
            )

        val extensionId = urlResult.extensionId
        val resourcePath = urlResult.resourcePath

        // 2. Fetch Extension from Registry
        val ext = registry.getExtension(extensionId)
            ?: throw ExtensionError.SecurityError.ExtensionNotFound(extensionId)

        if (!registry.isExtensionEnabled(extensionId)) {
            throw ExtensionError.SecurityError.AccessDenied(extensionId, "Extension is currently disabled")
        }

        // 3. Private Mode Policy Check
        if (isPrivate) {
            val allowedInPrivate = ext.allowedInPrivate ||
                    (permissionManager?.isAllowedInPrivate(extensionId) == true)
            if (!allowedInPrivate) {
                throw ExtensionError.SecurityError.AccessDenied(
                    extensionId,
                    "Extension disallowed in private browsing mode"
                )
            }
        }

        // 4. Resource Access Policy Check
        val accessDecision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = requestUrlStr,
            initiatorUrlStr = initiatorUrlStr,
            ext = ext,
            isPrivate = isPrivate
        )

        if (!accessDecision.isAllowed) {
            throw ExtensionError.SecurityError.ResourceNotAccessible(
                extensionId,
                resourcePath,
                "Web access to private extension resource is denied"
            )
        }

        // Handle dynamically generated background page
        if (resourcePath.equals("_generated_background_page.html", ignoreCase = true)) {
            val csp = ExtensionCspPolicy.getCspForExtension(ext, pageType)
            val headers = buildHeaders(extensionId, csp)
            val bootJs = ExtensionDirectoryResolver.bootstrapProvider?.invoke(extensionId) ?: ""
            val scriptTag = "<script>\n$bootJs\n</script>"
            val injectedHtml = "<!DOCTYPE html>\n<html>\n<head>\n$scriptTag\n</head>\n<body></body>\n</html>"
            val htmlBytes = injectedHtml.toByteArray(Charsets.UTF_8)

            return ExtensionResourceResult(
                extensionId = extensionId,
                resourcePath = resourcePath,
                mimeType = "text/html",
                encoding = "UTF-8",
                file = null,
                inputStreamProvider = { java.io.ByteArrayInputStream(htmlBytes) },
                headers = headers
            )
        }

        // 5. Canonical Path Containment Check
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, ext.name)
        val fileLookupPath = resourcePath.substringBefore('?').substringBefore('#')
        val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, fileLookupPath)
            ?: throw ExtensionError.SecurityError.ResourceNotAccessible(
                extensionId,
                resourcePath,
                "Requested resource does not exist on disk"
            )

        try {
            PathSanitizer.verifyCanonicalContainment(extensionDir, targetFile)
        } catch (e: Exception) {
            throw ExtensionError.SecurityError.PathTraversalDetected(resourcePath)
        }

        if (!targetFile.exists() || !targetFile.isFile) {
            throw ExtensionError.SecurityError.ResourceNotAccessible(
                extensionId,
                resourcePath,
                "Target file does not exist or is not a regular file"
            )
        }

        // 6. Response Headers, MIME Type & CSP Setup
        val mimeType = getMimeType(targetFile.extension)
        val encoding = getEncoding(targetFile.extension)
        val csp = ExtensionCspPolicy.getCspForExtension(ext, pageType)
        val headers = buildHeaders(extensionId, csp)

        return ExtensionResourceResult(
            extensionId = extensionId,
            resourcePath = resourcePath,
            mimeType = mimeType,
            encoding = encoding,
            file = targetFile,
            inputStreamProvider = { java.io.FileInputStream(targetFile) },
            headers = headers
        )
    }

    private fun buildHeaders(extensionId: String, csp: String): Map<String, String> {
        val headers = HashMap<String, String>()
        headers["Access-Control-Allow-Origin"] = "chrome-extension://$extensionId"
        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        headers["Access-Control-Allow-Headers"] = "*"
        headers["X-Content-Type-Options"] = "nosniff"
        headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
        headers["Content-Security-Policy"] = csp
        return headers
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }

    private fun getEncoding(extension: String): String? {
        return when (extension.lowercase()) {
            "html", "htm", "js", "mjs", "css", "json", "xml" -> "UTF-8"
            else -> null
        }
    }
}
