package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encapsulates a chrome.permissions.request() or query payload.
 */
data class ExtensionPermissionRequest(
    val extensionId: String,
    val permissions: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val sourceContext: String = "BACKGROUND", // "BACKGROUND", "POPUP", "CONTENT_SCRIPT", "OPTIONS"
    val tabId: Int? = null,
    val documentId: String? = null,
    val frameId: Int? = null,
    val origin: String? = null,
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null,
    val isUserGesture: Boolean = false,
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Standard Chrome Extension Permissions object representation:
 * { permissions: string[], origins: string[] }
 */
data class ExtensionPermissionsObject(
    val permissions: List<String> = emptyList(),
    val origins: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("permissions", JSONArray(permissions))
            put("origins", JSONArray(origins))
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): ExtensionPermissionsObject {
            if (json == null) return ExtensionPermissionsObject()
            val perms = mutableListOf<String>()
            val permArr = json.optJSONArray("permissions")
            if (permArr != null) {
                for (i in 0 until permArr.length()) {
                    val p = permArr.optString(i, "").trim()
                    if (p.isNotBlank()) perms.add(p)
                }
            }
            val origs = mutableListOf<String>()
            val origArr = json.optJSONArray("origins")
            if (origArr != null) {
                for (i in 0 until origArr.length()) {
                    val o = origArr.optString(i, "").trim()
                    if (o.isNotBlank()) origs.add(o)
                }
            }
            return ExtensionPermissionsObject(perms, origs)
        }
    }
}

sealed class ExtensionPermissionResult {
    data class Granted(val permissions: List<String>, val origins: List<String>) : ExtensionPermissionResult()
    data class Denied(val reason: String = "User denied permission request") : ExtensionPermissionResult()
    data class Error(val code: String, val message: String) : ExtensionPermissionResult()
}

object ExtensionPermissionErrors {
    const val USER_GESTURE_REQUIRED = "chrome.permissions.request() must be called from a user gesture."
    const val EXTENSION_NOT_FOUND = "Extension not found."
    const val EXTENSION_DISABLED = "Extension is disabled."
    const val NOT_DECLARED_OPTIONAL = "Requested permission is not specified in optional_permissions or optional_host_permissions in manifest."
    const val UNSUPPORTED_PERMISSION = "Requested permission is unsupported."
    const val INVALID_MATCH_PATTERN = "Invalid host match pattern."
    const val ANDROID_PERMISSION_DENIED = "Required Android system permission was denied."
    const val CANNOT_REMOVE_REQUIRED = "Cannot remove required permissions that were declared in 'permissions' or 'host_permissions'."
    const val UNKNOWN_ERROR = "An unexpected permission error occurred."
}
