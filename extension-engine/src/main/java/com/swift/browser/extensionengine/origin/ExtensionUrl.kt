package com.swift.browser.extensionengine.origin

import android.net.Uri
import com.swift.browser.extensionengine.PathSanitizer

/**
 * Parsed result of a browser extension URL.
 */
data class ExtensionUrlResult(
    val extensionId: String,
    val resourcePath: String,
    val canonicalUrl: String,
    val isSandbox: Boolean = false
)

/**
 * Production-Grade Extension URL Parsing, Construction, and Sanitization Engine.
 * Converts, validates, and parses extension-scheme URLs while strictly enforcing path safety.
 */
object ExtensionUrl {

    fun toExtensionUrl(extensionId: String, path: String): String {
        val cleanId = extensionId.lowercase().trim()
        val cleanPath = PathSanitizer.sanitizeRelativePath(path)
        return "${ExtensionOrigin.SCHEME_CHROME_EXTENSION}://$cleanId/$cleanPath"
    }

    fun parseExtensionUrl(urlStr: String?): ExtensionUrlResult? {
        if (urlStr.isNullOrBlank()) return null
        val trimmed = urlStr.trim()
        
        return try {
            val schemeSep = trimmed.indexOf("://")
            if (schemeSep <= 0) return null
            val scheme = trimmed.substring(0, schemeSep).lowercase()
            if (!ExtensionOrigin.isValidScheme(scheme)) return null

            val remainder = trimmed.substring(schemeSep + 3)
            val pathSep = remainder.indexOf('/')
            val host = if (pathSep >= 0) remainder.substring(0, pathSep).lowercase() else remainder.lowercase()
            if (host.isBlank() || !PathSanitizer.isSafeExtensionId(host)) return null

            // Extract relative path
            val rawPath = if (pathSep >= 0) remainder.substring(pathSep) else ""
            
            // Check for path traversal signs or absolute filesystem paths in raw path prior to decoding
            if (rawPath.contains("..") || rawPath.contains("\\") || rawPath.startsWith("//")) return null

            val cleanPath = PathSanitizer.sanitizeRelativePath(rawPath)
            
            // Re-verify that sanitized path does not attempt path traversal or escape
            if (!PathSanitizer.isSafeRelativePath(cleanPath)) return null

            val canonicalUrl = "${ExtensionOrigin.SCHEME_CHROME_EXTENSION}://$host/$cleanPath"
            
            ExtensionUrlResult(
                extensionId = host,
                resourcePath = cleanPath,
                canonicalUrl = canonicalUrl,
                isSandbox = false
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isExtensionUrl(urlStr: String?): Boolean {
        return parseExtensionUrl(urlStr) != null
    }

    fun getExtensionId(urlStr: String?): String? {
        return parseExtensionUrl(urlStr)?.extensionId
    }

    fun getResourcePath(urlStr: String?): String? {
        return parseExtensionUrl(urlStr)?.resourcePath
    }
}
