package com.swift.browser.permissionengine

import android.net.Uri
import java.net.URI

object OriginNormalizer {
    /**
     * Normalizes a URL, domain, or origin to its canonical permission identity.
     * Identity format: scheme + normalized host + effective port
     * Examples:
     *   "https://www.example.com:443/path?q=1" -> "https://www.example.com"
     *   "example.com" -> "https://example.com"
     *   "http://localhost:8080/api" -> "http://localhost:8080"
     */
    fun normalize(url: String): String {
        val trimmed = url.trim().lowercase()
        if (trimmed.isBlank()) return ""

        // Handle special pseudo-schemes
        if (trimmed.startsWith("about:") || trimmed.startsWith("swift:") || trimmed.startsWith("file:")) {
            return trimmed.substringBefore("/").substringBefore("?")
        }

        val withScheme = if (!trimmed.contains("://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        try {
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host?.lowercase() ?: ""
            val port = uri.port

            if (host.isNotBlank()) {
                val isStandardPort = (scheme == "http" && (port == -1 || port == 80)) ||
                        (scheme == "https" && (port == -1 || port == 443)) ||
                        (scheme == "ws" && (port == -1 || port == 80)) ||
                        (scheme == "wss" && (port == -1 || port == 443))

                return if (isStandardPort || port == -1) {
                    "$scheme://$host"
                } else {
                    "$scheme://$host:$port"
                }
            }
        } catch (_: Exception) {}

        try {
            val uri = Uri.parse(withScheme)
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host?.lowercase() ?: ""
            val port = uri.port

            if (host.isNotBlank()) {
                val isStandardPort = (scheme == "http" && (port == -1 || port == 80)) ||
                        (scheme == "https" && (port == -1 || port == 443)) ||
                        (scheme == "ws" && (port == -1 || port == 80)) ||
                        (scheme == "wss" && (port == -1 || port == 443))

                return if (isStandardPort || port == -1) {
                    "$scheme://$host"
                } else {
                    "$scheme://$host:$port"
                }
            }
        } catch (_: Exception) {}

        // Fallback: strip path/query
        val beforeSlash = withScheme.substringBefore("?").substringBefore("#")
        return if (beforeSlash.endsWith("/")) beforeSlash.dropLast(1) else beforeSlash
    }

    /**
     * Checks if the given normalized origin represents a secure context
     * according to W3C Secure Contexts specification:
     * - HTTPS / WSS schemes
     * - Localhost addresses (127.0.0.1, localhost, [::1])
     * - Swift internal schemes
     */
    fun isSecure(origin: String): Boolean {
        val normalized = normalize(origin).lowercase()
        if (normalized.startsWith("https://") || normalized.startsWith("wss://")) {
            return true
        }
        if (normalized.startsWith("http://localhost") ||
            normalized.startsWith("http://127.0.0.1") ||
            normalized.startsWith("http://[::1]")
        ) {
            return true
        }
        if (normalized.startsWith("about:") || normalized.startsWith("swift://") || normalized.startsWith("file://")) {
            return true
        }
        return false
    }

    fun isSecure(origin: DynamicOrigin): Boolean {
        if (origin.scheme == "https" || origin.scheme == "wss") return true
        if (origin.host == "localhost" || origin.host == "127.0.0.1" || origin.host == "::1" || origin.host == "[::1]") return true
        if (origin.scheme == "swift" || origin.scheme == "file" || origin.scheme == "about") return true
        return false
    }
}

