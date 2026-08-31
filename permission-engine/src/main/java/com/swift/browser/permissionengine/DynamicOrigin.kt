package com.swift.browser.permissionengine

import android.net.Uri
import java.net.URI

data class DynamicOrigin(
    val canonicalOrigin: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val topLevelOrigin: String,
    val frameOrigin: String? = null,
    val tabId: String = "default_tab",
    val isIncognito: Boolean = false,
    val isUserGesture: Boolean? = null,
    val requestSource: String = "website",
    val requestId: String = "req_" + java.util.UUID.randomUUID().toString().substring(0, 8),
    val apiName: String = "onPermissionRequest"
) {
    companion object {
        fun parse(
            rawUrl: String,
            topLevelUrl: String? = null,
            frameUrl: String? = null,
            tabId: String = "default_tab",
            isIncognito: Boolean = false,
            isUserGesture: Boolean? = null,
            requestSource: String = "website",
            requestId: String = "req_" + java.util.UUID.randomUUID().toString().substring(0, 8),
            apiName: String = "onPermissionRequest"
        ): DynamicOrigin {
            val normalized = OriginNormalizer.normalize(rawUrl)
            var scheme = ""
            var host = ""
            var port = -1

            try {
                val uri = URI(normalized)
                scheme = uri.scheme?.lowercase() ?: ""
                host = uri.host?.lowercase() ?: ""
                port = uri.port
            } catch (_: Exception) {
                try {
                    val uri = Uri.parse(normalized)
                    scheme = uri.scheme?.lowercase() ?: ""
                    host = uri.host?.lowercase() ?: ""
                    port = uri.port
                } catch (_: Exception) {}
            }

            if (scheme.isBlank() && normalized.contains("://")) {
                scheme = normalized.substringBefore("://").lowercase()
            }
            if (host.isBlank() && normalized.contains("://")) {
                host = normalized.substringAfter("://").substringBefore("/").substringBefore(":").lowercase()
            }

            val canonical = if (scheme.isNotBlank() && host.isNotBlank()) {
                if (port != -1 && port != 80 && port != 443) "$scheme://$host:$port" else "$scheme://$host"
            } else {
                normalized
            }

            val topLevelCanonical = if (!topLevelUrl.isNullOrBlank()) {
                OriginNormalizer.normalize(topLevelUrl)
            } else {
                canonical
            }

            val frameCanonical = if (!frameUrl.isNullOrBlank()) {
                OriginNormalizer.normalize(frameUrl)
            } else null

            return DynamicOrigin(
                canonicalOrigin = canonical,
                scheme = scheme,
                host = host,
                port = port,
                topLevelOrigin = topLevelCanonical,
                frameOrigin = frameCanonical,
                tabId = tabId,
                isIncognito = isIncognito,
                isUserGesture = isUserGesture,
                requestSource = requestSource,
                requestId = requestId,
                apiName = apiName
            )
        }
    }
}
