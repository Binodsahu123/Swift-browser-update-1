package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import com.swift.browser.cookieengine.CookieEngineApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * ExtensionCookieAccess provides a secure, permission-validated boundary
 * between extensions and the browser CookieEngine.
 *
 * It enforces:
 *  - URL validation (HTTP / HTTPS only)
 *  - Manifest host permission checks
 *  - CookieEngineApi routing (no direct CookieManager calls)
 *  - Value privacy (cookie values are never logged)
 */
class ExtensionCookieAccess(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry
) {
    private val cookieEngine by lazy { CookieEngineApi.getInstance(context) }

    fun isUrlValidForCookies(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            scheme == "http" || scheme == "https"
        } catch (e: Exception) {
            false
        }
    }

    fun hasCookieHostPermission(extensionId: String, url: String, isPrivate: Boolean = false): Boolean {
        if (isPrivate && !permissionManager.isAllowedInPrivate(extensionId)) return false
        if (!isUrlValidForCookies(url)) return false
        val ext = registry.getExtension(extensionId) ?: return false
        return permissionManager.hasHostPermission(extensionId, ext.hostPermissions, ext.permissions, url)
    }

    fun getCookie(extensionId: String, url: String, keyName: String, isPrivate: Boolean = false): JSONObject? {
        if (!hasCookieHostPermission(extensionId, url, isPrivate)) return null
        val cookiesStr = cookieEngine.getCookie(url) ?: ""
        val cookiesMap = cookiesStr.split(";").associate {
            val parts = it.split("=", limit = 2)
            val k = parts.getOrNull(0)?.trim() ?: ""
            val v = parts.getOrNull(1)?.trim() ?: ""
            k to v
        }
        val cookieVal = cookiesMap[keyName] ?: return null
        val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        return JSONObject().apply {
            put("name", keyName)
            put("value", cookieVal)
            put("domain", host)
            put("path", "/")
        }
    }

    fun getAllCookies(extensionId: String, url: String, isPrivate: Boolean = false): JSONArray? {
        if (!hasCookieHostPermission(extensionId, url, isPrivate)) return null
        val cookiesStr = cookieEngine.getCookie(url) ?: ""
        val list = JSONArray()
        val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        cookiesStr.split(";").forEach {
            val parts = it.split("=", limit = 2)
            val k = parts.getOrNull(0)?.trim() ?: ""
            val v = parts.getOrNull(1)?.trim() ?: ""
            if (k.isNotBlank()) {
                list.put(JSONObject().apply {
                    put("name", k)
                    put("value", v)
                    put("domain", host)
                    put("path", "/")
                })
            }
        }
        return list
    }

    fun setCookie(extensionId: String, url: String, keyName: String, value: String, isPrivate: Boolean = false): Boolean {
        if (!hasCookieHostPermission(extensionId, url, isPrivate)) return false
        if (keyName.isBlank()) return false
        cookieEngine.setCookie(url, "$keyName=$value")
        cookieEngine.flush()
        return true
    }

    fun removeCookie(extensionId: String, url: String, keyName: String, isPrivate: Boolean = false): Boolean {
        if (!hasCookieHostPermission(extensionId, url, isPrivate)) return false
        if (keyName.isBlank()) return false
        cookieEngine.setCookie(url, "$keyName=; Max-Age=-99999999; expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
        cookieEngine.flush()
        return true
    }
}
