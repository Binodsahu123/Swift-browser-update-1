package com.swift.browser.videoengine.live

import java.net.URI

data class RtmpUrl(
    val host: String,
    val port: Int,
    val appName: String,
    val streamKey: String = "",
    val tcUrl: String,
    val isSecure: Boolean = false
) {
    fun buildFullUrl(keyOverride: String? = null): String {
        val keyToUse = if (!keyOverride.isNullOrBlank()) keyOverride else streamKey
        val base = tcUrl.removeSuffix("/")
        return if (keyToUse.isNotBlank()) "$base/${keyToUse.trim('/')}" else base
    }
}

object RtmpUrlParser {
    fun parse(urlStr: String): RtmpUrl {
        val cleaned = urlStr.trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("RTMP URL cannot be empty")
        }

        val isSecure = cleaned.startsWith("rtmps://", ignoreCase = true)
        val isExplicitRtmp = cleaned.startsWith("rtmp://", ignoreCase = true)

        if (cleaned.contains("://") && !isSecure && !isExplicitRtmp) {
            throw IllegalArgumentException("Invalid protocol. Only rtmp:// and rtmps:// are supported.")
        }

        val uriString = if (!cleaned.contains("://")) {
            if (isSecure) "rtmps://$cleaned" else "rtmp://$cleaned"
        } else {
            cleaned
        }

        val uri = try {
            URI.create(uriString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid RTMP URL structure: ${e.message}", e)
        }

        val host = uri.host ?: throw IllegalArgumentException("Missing host in RTMP URL")
        if (host.isBlank()) {
            throw IllegalArgumentException("Host cannot be empty in RTMP URL")
        }

        val parsedPort = uri.port
        val defaultPort = if (isSecure) 443 else 1935
        val port = if (parsedPort != -1) parsedPort else defaultPort

        val path = uri.path ?: ""
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }

        if (parts.isEmpty()) {
            throw IllegalArgumentException("Invalid RTMP application path: missing application name")
        }

        val appName = parts[0]
        val streamKey = if (parts.size > 1) parts.drop(1).joinToString("/") else ""

        val scheme = if (isSecure) "rtmps" else "rtmp"
        val tcUrl = "$scheme://$host:$port/$appName"

        return RtmpUrl(
            host = host,
            port = port,
            appName = appName,
            streamKey = streamKey,
            tcUrl = tcUrl,
            isSecure = isSecure
        )
    }

    /**
     * Builds a normalized RTMP/RTMPS server URL or full stream URL without duplicating ports.
     */
    fun buildUrl(
        serverUrl: String,
        port: Int = 1935,
        protocol: StreamingProtocol = StreamingProtocol.RTMP,
        tlsRequired: Boolean = false,
        streamKey: String = ""
    ): String {
        var cleanUrl = serverUrl.trim()
        if (cleanUrl.isEmpty()) return ""

        if (cleanUrl.startsWith("rtmp://", ignoreCase = true)) {
            cleanUrl = cleanUrl.substring(7)
        } else if (cleanUrl.startsWith("rtmps://", ignoreCase = true)) {
            cleanUrl = cleanUrl.substring(8)
        }
        cleanUrl = cleanUrl.trim('/')

        val isSecure = protocol == StreamingProtocol.RTMPS || tlsRequired
        val scheme = if (isSecure) "rtmps" else "rtmp"

        val hostPart = cleanUrl.substringBefore("/")
        val pathPart = if (cleanUrl.contains("/")) "/" + cleanUrl.substringAfter("/") else ""

        // Check if hostPart already specifies a port (e.g. "a.rtmp.youtube.com:443")
        val urlHasPort = hostPart.contains(":")
        val formattedHost = if (urlHasPort) {
            hostPart // Already has port in serverUrl, do NOT append ":port"
        } else {
            val defaultPort = if (isSecure) 443 else 1935
            if (port > 0 && port != defaultPort) {
                "$hostPart:$port"
            } else {
                hostPart
            }
        }

        val baseUrl = "$scheme://$formattedHost$pathPart"
        return if (streamKey.isNotBlank()) {
            val keyClean = streamKey.trim('/')
            if (baseUrl.endsWith("/")) "$baseUrl$keyClean" else "$baseUrl/$keyClean"
        } else {
            baseUrl
        }
    }
}

