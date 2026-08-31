package com.swift.browser.extensionengine

import java.util.regex.Pattern

class HostPattern(val pattern: String) {
    val rawPattern: String = pattern.trim()
    val isAllUrls: Boolean = rawPattern == "<all_urls>"

    val scheme: String
    val host: String
    val path: String

    init {
        if (isAllUrls) {
            scheme = "*"
            host = "*"
            path = "/*"
        } else {
            val schemeSep = rawPattern.indexOf("://")
            if (schemeSep > 0) {
                scheme = rawPattern.substring(0, schemeSep).lowercase()
                val remainder = rawPattern.substring(schemeSep + 3)
                val pathSep = remainder.indexOf('/')
                if (pathSep >= 0) {
                    host = remainder.substring(0, pathSep).lowercase()
                    path = remainder.substring(pathSep)
                } else {
                    host = remainder.lowercase()
                    path = "/*"
                }
            } else {
                scheme = "*"
                host = "*"
                path = "/*"
            }
        }
    }

    fun matchesUrl(urlStr: String): Boolean {
        if (urlStr.isBlank()) return false
        if (isAllUrls) return true

        val trimmed = urlStr.trim()
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) return false

        val urlScheme = trimmed.substring(0, schemeSep).lowercase()
        val remainder = trimmed.substring(schemeSep + 3)
        val pathSep = remainder.indexOf('/')
        val urlHost = (if (pathSep >= 0) remainder.substring(0, pathSep) else remainder).lowercase()
        val urlPath = if (pathSep >= 0) remainder.substring(pathSep) else "/"

        // Scheme check
        if (scheme != "*") {
            if (scheme != urlScheme) return false
        } else {
            if (urlScheme != "http" && urlScheme != "https") return false
        }

        // Host check
        if (host != "*") {
            if (host.startsWith("*.")) {
                val domain = host.substring(2)
                if (urlHost != domain && !urlHost.endsWith(".$domain")) return false
            } else if (host != urlHost) {
                return false
            }
        }

        // Path check
        if (path == "/*" || path == "*") return true

        val regexStr = "^" + Pattern.quote(path).replace("*", "\\E.*\\Q") + "$"
        return try {
            Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(urlPath).matches()
        } catch (e: Exception) {
            false
        }
    }
}

data class ExtensionMatchPattern(val patternStr: String) {
    private val hostPattern = HostPattern(patternStr)

    val rawPattern: String get() = hostPattern.rawPattern
    val isAllUrls: Boolean get() = hostPattern.isAllUrls

    fun matches(urlStr: String): Boolean {
        return hostPattern.matchesUrl(urlStr)
    }

    companion object {
        fun matchesAny(urlStr: String, patterns: List<String>): Boolean {
            if (patterns.isEmpty() || urlStr.isBlank()) return false
            return patterns.any { ExtensionMatchPattern(it).matches(urlStr) }
        }
    }
}
