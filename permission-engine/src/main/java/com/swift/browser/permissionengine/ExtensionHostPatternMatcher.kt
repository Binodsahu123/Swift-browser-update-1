package com.swift.browser.permissionengine

import java.util.regex.Pattern

/**
 * Canonical Host and URL Match Pattern parser and matcher for extension host permissions.
 * Conforms to Chrome Extension Match Patterns specification.
 */
object ExtensionHostPatternMatcher {

    class ParsedPattern(val rawPattern: String) {
        val isAllUrls: Boolean = rawPattern.trim() == "<all_urls>"
        val scheme: String
        val host: String
        val path: String
        val isValid: Boolean

        init {
            val trimmed = rawPattern.trim()
            if (isAllUrls) {
                scheme = "*"
                host = "*"
                path = "/*"
                isValid = true
            } else {
                val schemeSep = trimmed.indexOf("://")
                if (schemeSep > 0) {
                    val parsedScheme = trimmed.substring(0, schemeSep).lowercase()
                    val remainder = trimmed.substring(schemeSep + 3)
                    val pathSep = remainder.indexOf('/')
                    val parsedHost = (if (pathSep >= 0) remainder.substring(0, pathSep) else remainder).lowercase()
                    val parsedPath = if (pathSep >= 0) remainder.substring(pathSep) else "/*"

                    scheme = parsedScheme
                    host = parsedHost
                    path = parsedPath
                    isValid = isValidScheme(scheme) && isValidHost(host) && isValidPath(path)
                } else {
                    scheme = "*"
                    host = "*"
                    path = "/*"
                    isValid = false
                }
            }
        }

        private fun isValidScheme(s: String): Boolean {
            return s == "*" || s == "http" || s == "https" || s == "file" || s == "ws" || s == "wss" || s == "ftp"
        }

        private fun isValidHost(h: String): Boolean {
            if (h == "*") return true
            if (h.isBlank()) return false
            if (h.startsWith("*.")) {
                val domain = h.substring(2)
                return domain.isNotBlank() && !domain.contains("*")
            }
            return !h.contains("*")
        }

        private fun isValidPath(p: String): Boolean {
            return p.startsWith("/")
        }

        fun matchesUrl(urlStr: String): Boolean {
            if (urlStr.isBlank() || !isValid) return false
            if (isAllUrls) {
                val trimmed = urlStr.trim().lowercase()
                return trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                        trimmed.startsWith("ws://") || trimmed.startsWith("wss://") ||
                        trimmed.startsWith("file://") || trimmed.startsWith("ftp://")
            }

            val trimmed = urlStr.trim()
            val schemeSep = trimmed.indexOf("://")
            if (schemeSep <= 0) return false

            val urlScheme = trimmed.substring(0, schemeSep).lowercase()
            val remainder = trimmed.substring(schemeSep + 3)
            val pathSep = remainder.indexOf('/')
            val rawHostPort = if (pathSep >= 0) remainder.substring(0, pathSep) else remainder
            val urlHost = (if (rawHostPort.contains(':')) rawHostPort.substringBefore(':') else rawHostPort).lowercase()
            val urlPath = if (pathSep >= 0) remainder.substring(pathSep) else "/"

            // Scheme Check
            if (scheme != "*") {
                if (scheme != urlScheme) return false
            } else {
                if (urlScheme != "http" && urlScheme != "https" && urlScheme != "ws" && urlScheme != "wss") {
                    return false
                }
            }

            // Host Check
            if (host != "*") {
                if (host.startsWith("*.")) {
                    val rootDomain = host.substring(2)
                    if (urlHost != rootDomain && !urlHost.endsWith(".$rootDomain")) {
                        return false
                    }
                } else {
                    if (host != urlHost) return false
                }
            }

            // Path Check
            if (path == "/*" || path == "*") return true

            return try {
                val regexStr = "^" + Pattern.quote(path).replace("*", "\\E.*\\Q") + "$"
                Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(urlPath).matches()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun matches(pattern: String, urlStr: String): Boolean {
        if (pattern.isBlank() || urlStr.isBlank()) return false
        return ParsedPattern(pattern).matchesUrl(urlStr)
    }

    fun matchesAny(patterns: Collection<String>, urlStr: String): Boolean {
        if (patterns.isEmpty() || urlStr.isBlank()) return false
        for (p in patterns) {
            if (matches(p, urlStr)) return true
        }
        return false
    }

    fun isValidPattern(pattern: String): Boolean {
        if (pattern.isBlank()) return false
        return ParsedPattern(pattern).isValid
    }

    fun normalizePattern(pattern: String): String {
        val trimmed = pattern.trim()
        if (trimmed == "<all_urls>") return "<all_urls>"
        val parsed = ParsedPattern(trimmed)
        if (!parsed.isValid) return trimmed
        val s = parsed.scheme
        val h = parsed.host
        val p = parsed.path
        return "$s://$h$p"
    }

    /**
     * Checks if childPattern is a valid subset of parentPattern.
     * Used to verify that requested optional host permissions are contained within or permitted
     * by declared optional host patterns.
     */
    fun isSubsetPattern(childPattern: String, parentPattern: String): Boolean {
        val child = ParsedPattern(childPattern)
        val parent = ParsedPattern(parentPattern)

        if (!child.isValid || !parent.isValid) return false
        if (parent.isAllUrls) return true
        if (child.isAllUrls && !parent.isAllUrls) return false

        // Scheme subset check
        if (parent.scheme != "*") {
            if (child.scheme != parent.scheme) return false
        }

        // Host subset check
        if (parent.host != "*") {
            if (parent.host.startsWith("*.")) {
                val parentDomain = parent.host.substring(2)
                if (child.host.startsWith("*.")) {
                    val childDomain = child.host.substring(2)
                    if (childDomain != parentDomain && !childDomain.endsWith(".$parentDomain")) {
                        return false
                    }
                } else {
                    if (child.host != parentDomain && !child.host.endsWith(".$parentDomain")) {
                        return false
                    }
                }
            } else {
                if (child.host != parent.host) return false
            }
        }

        // Path subset check
        if (parent.path != "/*" && parent.path != "*") {
            if (child.path != parent.path && !child.path.startsWith(parent.path.removeSuffix("*"))) {
                return false
            }
        }

        return true
    }
}
