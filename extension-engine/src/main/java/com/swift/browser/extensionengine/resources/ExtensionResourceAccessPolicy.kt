package com.swift.browser.extensionengine.resources

import com.swift.browser.extensionengine.ParsedExtension
import com.swift.browser.extensionengine.PermissionManager
import com.swift.browser.extensionengine.origin.ExtensionOrigin
import com.swift.browser.extensionengine.origin.ExtensionUrl
import org.json.JSONArray
import org.json.JSONObject

enum class ResourceAccessType {
    PRIVATE_EXTENSION_RESOURCE,
    WEB_ACCESSIBLE_RESOURCE,
    SANDBOX_RESOURCE,
    EXECUTABLE_SCRIPT,
    PAGE_RESOURCE,
    ICON,
    MANIFEST
}

enum class AccessDecision {
    ALLOW,
    DENY;

    val isAllowed: Boolean get() = this == ALLOW
}

/**
 * Production-Grade Resource Access Policy Engine for Browser Extensions.
 * Evaluates whether web pages, external scripts, or other extensions are permitted
 * to request or load resources from a target extension.
 */
object ExtensionResourceAccessPolicy {

    fun classifyResource(resourcePath: String, ext: ParsedExtension): ResourceAccessType {
        val cleanPath = resourcePath.lowercase().removePrefix("/")
        return when {
            cleanPath == "manifest.json" -> ResourceAccessType.MANIFEST
            cleanPath.endsWith(".png") || cleanPath.endsWith(".jpg") || cleanPath.endsWith(".svg") || cleanPath.endsWith(".ico") -> ResourceAccessType.ICON
            cleanPath.endsWith(".html") || cleanPath.endsWith(".htm") -> ResourceAccessType.PAGE_RESOURCE
            cleanPath.endsWith(".js") || cleanPath.endsWith(".mjs") -> ResourceAccessType.EXECUTABLE_SCRIPT
            isWebAccessibleResource(resourcePath, ext) -> ResourceAccessType.WEB_ACCESSIBLE_RESOURCE
            else -> ResourceAccessType.PRIVATE_EXTENSION_RESOURCE
        }
    }

    fun evaluateAccess(
        requestUrlStr: String,
        initiatorUrlStr: String?,
        ext: ParsedExtension,
        isPrivate: Boolean = false
    ): AccessDecision {
        val requestExtId = ExtensionUrl.getExtensionId(requestUrlStr) ?: return AccessDecision.DENY
        val resourcePath = ExtensionUrl.getResourcePath(requestUrlStr) ?: return AccessDecision.DENY

        // Direct request from same extension is ALWAYS allowed
        val initiatorExtId = ExtensionUrl.getExtensionId(initiatorUrlStr)
        if (initiatorExtId != null && initiatorExtId.equals(requestExtId, ignoreCase = true)) {
            return AccessDecision.ALLOW
        }

        // Internal background page or popup request without explicit initiator URL is allowed
        if (initiatorUrlStr.isNullOrBlank()) {
            return AccessDecision.ALLOW
        }

        // Web or Cross-Origin request: MUST match web_accessible_resources
        val isAccessible = isWebAccessible(resourcePath, initiatorUrlStr, ext)
        return if (isAccessible) AccessDecision.ALLOW else AccessDecision.DENY
    }

    private fun isWebAccessibleResource(resourcePath: String, ext: ParsedExtension): Boolean {
        return isWebAccessible(resourcePath, null, ext)
    }

    private fun isWebAccessible(
        resourcePath: String,
        initiatorUrlStr: String?,
        ext: ParsedExtension
    ): Boolean {
        val cleanPath = resourcePath.removePrefix("/").lowercase()
        val root = try {
            JSONObject(ext.manifestJson)
        } catch (e: Exception) {
            return false
        }

        if (!root.has("web_accessible_resources")) return false

        val warObj = root.opt("web_accessible_resources") ?: return false

        // Manifest V3 format: JSONArray of Objects [{ "resources": ["..."], "matches": ["..."], "extension_ids": ["..."] }]
        if (warObj is JSONArray && ext.manifestVersion >= 3) {
            for (i in 0 until warObj.length()) {
                val entry = warObj.optJSONObject(i)
                if (entry != null) {
                    val resourcesArray = entry.optJSONArray("resources") ?: continue

                    var pathMatches = false
                    for (j in 0 until resourcesArray.length()) {
                        val pattern = resourcesArray.optString(j, "")
                        if (matchesResourcePattern(cleanPath, pattern)) {
                            pathMatches = true
                            break
                        }
                    }

                    if (!pathMatches) continue

                    val matchesArray = entry.optJSONArray("matches")
                    val extIdsArray = entry.optJSONArray("extension_ids")

                    if (initiatorUrlStr.isNullOrBlank()) {
                        return true
                    }

                    val initiatorExtId = ExtensionUrl.getExtensionId(initiatorUrlStr)
                    if (initiatorExtId != null && extIdsArray != null) {
                        for (k in 0 until extIdsArray.length()) {
                            val allowedId = extIdsArray.optString(k, "")
                            if (allowedId == "*" || allowedId.equals(initiatorExtId, ignoreCase = true)) {
                                return true
                            }
                        }
                    }

                    if (matchesArray != null) {
                        for (k in 0 until matchesArray.length()) {
                            val matchPattern = matchesArray.optString(k, "")
                            if (matchPattern == "<all_urls>" || matchPattern == "*://*/*" ||
                                PermissionManager.matchHostPattern(initiatorUrlStr, matchPattern)) {
                                return true
                            }
                        }
                    } else if (extIdsArray == null) {
                        return true
                    }
                } else {
                    // String pattern inside MV3 array fallback
                    val pattern = warObj.optString(i, "")
                    if (matchesResourcePattern(cleanPath, pattern)) {
                        return true
                    }
                }
            }
            return false
        }

        // Manifest V2 format: JSONArray of glob patterns ["images/*", "inject.js"]
        if (warObj is JSONArray) {
            for (i in 0 until warObj.length()) {
                val pattern = warObj.optString(i, "")
                if (matchesResourcePattern(cleanPath, pattern)) {
                    return true
                }
            }
            return false
        }

        return false
    }

    private fun matchesResourcePattern(cleanPath: String, pattern: String): Boolean {
        val cleanPattern = pattern.removePrefix("/").lowercase()
        if (cleanPattern == "*" || cleanPattern == "*/*") return true

        if (cleanPattern.contains("*")) {
            val regexStr = "^" + cleanPattern.replace(".", "\\.").replace("*", ".*") + "$"
            return try {
                Regex(regexStr).matches(cleanPath)
            } catch (e: Exception) {
                false
            }
        }

        return cleanPath.equals(cleanPattern, ignoreCase = true)
    }
}
