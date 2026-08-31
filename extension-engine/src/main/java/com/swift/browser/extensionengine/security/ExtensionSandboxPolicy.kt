package com.swift.browser.extensionengine.security

import com.swift.browser.extensionengine.ParsedExtension
import com.swift.browser.extensionengine.origin.ExtensionUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sandboxed Extension Page Security Engine.
 * Manages detection, origin isolation, and CSP enforcement for manifest-declared sandboxed pages.
 */
object ExtensionSandboxPolicy {

    const val SANDBOX_UNIQUE_ORIGIN = "null"

    fun isSandboxedPage(urlStr: String?, ext: ParsedExtension): Boolean {
        if (urlStr.isNullOrBlank()) return false
        val resourcePath = ExtensionUrl.getResourcePath(urlStr) ?: return false
        val cleanPath = resourcePath.lowercase().removePrefix("/")

        return try {
            val root = JSONObject(ext.manifestJson)
            val sandboxObj = root.opt("sandbox") ?: return false

            if (sandboxObj is JSONObject) {
                val pages = sandboxObj.optJSONArray("pages")
                if (pages != null) {
                    for (i in 0 until pages.length()) {
                        val pagePattern = pages.optString(i, "").lowercase().removePrefix("/")
                        if (matchesPagePattern(cleanPath, pagePattern)) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun matchesPagePattern(cleanPath: String, pattern: String): Boolean {
        if (pattern == cleanPath) return true
        if (pattern.contains("*")) {
            val regex = Regex("^" + pattern.replace(".", "\\.").replace("*", ".*") + "$")
            return regex.matches(cleanPath)
        }
        return false
    }

    fun isPrivilegedAccessAllowed(isSandboxed: Boolean): Boolean {
        return !isSandboxed
    }
}
