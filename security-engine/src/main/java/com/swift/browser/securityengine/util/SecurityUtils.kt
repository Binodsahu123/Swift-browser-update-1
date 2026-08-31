package com.swift.browser.securityengine.util

import java.net.URI

object SecurityUtils {
    fun extractHost(url: String): String {
        return try {
            val uri = URI(url)
            uri.host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun isLocalOrInternalUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("about:") ||
                lower.startsWith("swift://") ||
                lower.startsWith("file://") ||
                lower.startsWith("content://") ||
                lower.contains("localhost") ||
                lower.contains("127.0.0.1")
    }

    fun sanitizeUrl(url: String): String {
        return url.trim()
    }
}
