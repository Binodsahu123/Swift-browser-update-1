package com.swift.browser.extensionengine.origin

import com.swift.browser.extensionengine.ExtensionError
import com.swift.browser.extensionengine.PathSanitizer
import java.io.File

/**
 * Production-Grade Canonical Extension Origin representation.
 * Represents an isolated, stable browser extension origin:
 *   chrome-extension://<extensionId>/
 *
 * This origin abstraction replaces file:// loading and isolates extension page contexts
 * from web pages, local file systems, and other extensions.
 */
data class ExtensionOrigin(
    val extensionId: String,
    val resourceRoot: File? = null
) {
    val scheme: String = SCHEME_CHROME_EXTENSION
    val host: String = extensionId.lowercase().trim()
    val origin: String = "$scheme://$host/"

    init {
        if (host.isBlank()) {
            throw ExtensionError.SecurityError.InvalidExtensionOrigin(
                "",
                "Extension ID cannot be blank for origin creation"
            )
        }
        if (!PathSanitizer.isSafeExtensionId(host)) {
            throw ExtensionError.SecurityError.InvalidExtensionOrigin(
                host,
                "Invalid or unsafe extension ID format in origin"
            )
        }
    }

    fun isSameOrigin(otherUrlStr: String?): Boolean {
        if (otherUrlStr == null) return false
        val other = fromUrl(otherUrlStr) ?: return false
        return this.host.equals(other.host, ignoreCase = true)
    }

    override fun toString(): String = origin

    companion object {
        const val SCHEME_CHROME_EXTENSION = "chrome-extension"
        const val SCHEME_SWIFT_EXTENSION = "swift-extension"

        fun fromExtensionId(extensionId: String, resourceRoot: File? = null): ExtensionOrigin {
            return ExtensionOrigin(extensionId = extensionId.trim(), resourceRoot = resourceRoot)
        }

        fun fromUrl(urlStr: String, resourceRoot: File? = null): ExtensionOrigin? {
            if (urlStr.isBlank()) return null
            return try {
                val trimmed = urlStr.trim()
                val schemeSep = trimmed.indexOf("://")
                if (schemeSep <= 0) return null
                val scheme = trimmed.substring(0, schemeSep).lowercase()
                if (!isValidScheme(scheme)) return null
                val remainder = trimmed.substring(schemeSep + 3)
                val pathSep = remainder.indexOf('/')
                val host = if (pathSep >= 0) remainder.substring(0, pathSep).lowercase() else remainder.lowercase()
                if (host.isBlank()) return null
                ExtensionOrigin(extensionId = host, resourceRoot = resourceRoot)
            } catch (e: Exception) {
                null
            }
        }

        fun isValidScheme(scheme: String?): Boolean {
            if (scheme == null) return false
            val s = scheme.lowercase().trim()
            return s == SCHEME_CHROME_EXTENSION || s == SCHEME_SWIFT_EXTENSION
        }

        private fun String?.isNull0rBlank(): Boolean = this == null || this.trim().isEmpty()
    }
}
