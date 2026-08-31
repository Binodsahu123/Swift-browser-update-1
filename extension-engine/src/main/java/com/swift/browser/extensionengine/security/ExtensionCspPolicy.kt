package com.swift.browser.extensionengine.security

import com.swift.browser.extensionengine.ParsedExtension
import org.json.JSONObject

/**
 * Production-Grade Content Security Policy (CSP) Manager for Browser Extensions.
 * Parses and enforces Manifest V2 and Manifest V3 CSP specifications safely.
 */
object ExtensionCspPolicy {

    const val DEFAULT_MV2_EXTENSION_CSP = "script-src 'self' 'unsafe-eval'; object-src 'self';"
    const val DEFAULT_MV3_EXTENSION_CSP = "script-src 'self'; object-src 'self';"
    const val DEFAULT_SANDBOX_CSP = "sandbox allow-scripts allow-forms allow-popups; script-src 'self' 'unsafe-inline' 'unsafe-eval';"

    fun getCspForExtension(ext: ParsedExtension, pageType: ExtensionPageType): String {
        if (pageType == ExtensionPageType.SANDBOX_PAGE) {
            return getSandboxCsp(ext)
        }

        return getExtensionPageCsp(ext)
    }

    fun getExtensionPageCsp(ext: ParsedExtension): String {
        return try {
            val root = JSONObject(ext.manifestJson)
            val mv = ext.manifestVersion

            if (root.has("content_security_policy")) {
                val cspObj = root.opt("content_security_policy")
                if (cspObj is String && cspObj.isNotBlank()) {
                    return sanitizeCsp(cspObj, mv)
                } else if (cspObj is JSONObject) {
                    val pageCsp = cspObj.optString("extension_pages", "")
                    if (pageCsp.isNotBlank()) {
                        return sanitizeCsp(pageCsp, mv)
                    }
                }
            }

            if (mv >= 3) DEFAULT_MV3_EXTENSION_CSP else DEFAULT_MV2_EXTENSION_CSP
        } catch (e: Exception) {
            if (ext.manifestVersion >= 3) DEFAULT_MV3_EXTENSION_CSP else DEFAULT_MV2_EXTENSION_CSP
        }
    }

    fun getSandboxCsp(ext: ParsedExtension): String {
        return try {
            val root = JSONObject(ext.manifestJson)
            val cspObj = root.opt("content_security_policy")

            if (cspObj is JSONObject) {
                val sandboxCsp = cspObj.optString("sandbox", "")
                if (sandboxCsp.isNotBlank()) {
                    return ensureSandboxDirective(sandboxCsp)
                }
            } else if (root.has("sandbox")) {
                val sandboxObj = root.optJSONObject("sandbox")
                val sandboxCsp = sandboxObj?.optString("content_security_policy", "")
                if (!sandboxCsp.isNullOrBlank()) {
                    return ensureSandboxDirective(sandboxCsp)
                }
            }

            DEFAULT_SANDBOX_CSP
        } catch (e: Exception) {
            DEFAULT_SANDBOX_CSP
        }
    }

    private fun sanitizeCsp(csp: String, manifestVersion: Int): String {
        val trimmed = csp.trim()
        if (manifestVersion >= 3) {
            // Manifest V3 strictly prohibits 'unsafe-eval' and remote HTTP/HTTPS script sources in extension pages
            var mv3Csp = trimmed
            if (mv3Csp.contains("'unsafe-eval'")) {
                mv3Csp = mv3Csp.replace("'unsafe-eval'", "").replace("  ", " ")
            }
            return mv3Csp
        }
        return trimmed
    }

    private fun ensureSandboxDirective(csp: String): String {
        val trimmed = csp.trim()
        if (!trimmed.lowercase().contains("sandbox")) {
            return "sandbox allow-scripts allow-forms allow-popups; $trimmed"
        }
        return trimmed
    }
}
