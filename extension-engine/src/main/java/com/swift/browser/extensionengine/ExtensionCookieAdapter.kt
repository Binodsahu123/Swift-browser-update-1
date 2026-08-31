package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import com.swift.browser.cookieengine.CookieChangeEvent
import com.swift.browser.cookieengine.CookieEngine
import com.swift.browser.cookieengine.CookieEngineApi
import com.swift.browser.tabengine.api.TabEngineApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ExtensionCookieAdapter bridges chrome.cookies.* API calls to Orion's CookieEngine
 * as the authoritative single source of truth, enforcing strict multi-profile isolation,
 * permission validation, and event dispatching.
 */
class ExtensionCookieAdapter(
    private val context: Context,
    private val permissionAdapter: ExtensionPermissionAdapter,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager,
    private val tabEngine: TabEngineApi? = null,
    private val cookieEngine: CookieEngine = CookieEngineApi.getInstance(context)
) {

    init {
        cookieEngine.addCookieChangeListener { event ->
            handleCookieChangeEvent(event)
        }
    }

    /**
     * Validates that the URL is a valid HTTP/HTTPS URL.
     */
    private fun isUrlValidForCookies(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            scheme == "http" || scheme == "https"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Rejects CR/LF characters to prevent header injection.
     */
    private fun validateCookieField(value: String?, fieldName: String) {
        if (value == null) return
        if (value.contains('\r') || value.contains('\n')) {
            throw IllegalArgumentException("Invalid character in cookie $fieldName: CR/LF injection detected.")
        }
    }

    /**
     * Canonical cookie-domain matching.
     * Boundary: example.com must not match evil-example.com.
     */
    private fun domainMatches(host: String, domain: String): Boolean {
        val cleanHost = host.lowercase().removePrefix(".")
        val cleanDomain = domain.lowercase().removePrefix(".")
        return cleanHost == cleanDomain || cleanHost.endsWith(".$cleanDomain")
    }

    /**
     * Verifies API permission, private context allowance, URL format, and host permissions.
     */
    private fun verifyCookiePermissions(sender: ExtensionSender, url: String): Boolean {
        val ext = registry.getExtension(sender.extensionId) ?: return false

        // Check 'cookies' API permission via canonical adapter
        if (!permissionAdapter.hasApiPermission(sender.extensionId, "cookies", sender.isPrivate)) {
            return false
        }

        // Check private mode permission if sender is in private browsing context
        if (sender.isPrivate && !permissionAdapter.isAllowedInPrivate(sender.extensionId)) {
            return false
        }

        // Validate URL format (must be http/https)
        if (!isUrlValidForCookies(url)) {
            return false
        }

        // Validate host permission against canonical matcher
        return permissionAdapter.hasHostPermission(sender.extensionId, url, sender.isPrivate)
    }

    /**
     * Resolves target profile name based on sender context and requested storeId.
     * ABSOLUTE RULE: Never fallback to normal/default profile if private profile is unavailable.
     */
    private fun resolveTargetProfile(sender: ExtensionSender, storeId: String?): String? {
        val isPrivateRequested = sender.isPrivate || storeId == "1" || storeId == "private" || storeId == "incognito"
        if (isPrivateRequested) {
            if (!permissionAdapter.isAllowedInPrivate(sender.extensionId)) {
                return null
            }
            val sessionId = sender.privateSessionId
            if (sessionId.isNullOrBlank()) {
                // Cannot proceed in private mode without valid privateSessionId
                return null
            }
            val profileName = CookieEngine.getPrivateProfileName(sessionId)
            val profileCm = cookieEngine.getProfileCookieManager(profileName)
            if (profileCm == null) {
                // Strict isolation: Never fall back to normal/default CookieManager!
                return null
            }
            return profileName
        }
        return "default"
    }

    /**
     * chrome.cookies.get(details)
     */
    fun get(sender: ExtensionSender, details: JSONObject): JSONObject? {
        val url = details.optString("url")
        val name = details.optString("name")
        val storeId = if (details.has("storeId")) details.getString("storeId") else null

        validateCookieField(name, "name")

        if (!verifyCookiePermissions(sender, url)) {
            throw SecurityException("SecurityError: Permission denied or invalid host/URL for cookies.")
        }

        if (name.isBlank()) return null

        val profileName = resolveTargetProfile(sender, storeId) ?: return null
        val cookiesStr = cookieEngine.getCookie(profileName, url) ?: ""
        if (cookiesStr.isBlank()) return null

        val cookiesMap = cookiesStr.split(";").associate {
            val parts = it.split("=", limit = 2)
            val k = parts.getOrNull(0)?.trim() ?: ""
            val v = parts.getOrNull(1)?.trim() ?: ""
            k to v
        }

        val cookieVal = cookiesMap[name] ?: return null
        val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        val isPrivateStore = sender.isPrivate || storeId == "1" || storeId == "private" || storeId == "incognito"

        // Return only honest actual metadata available from WebView
        return JSONObject().apply {
            put("name", name)
            put("value", cookieVal)
            put("domain", host)
            put("path", (Uri.parse(url).path ?: "/").ifBlank { "/" })
            put("storeId", if (isPrivateStore) "1" else "0")
            put("metadataPartial", true)
        }
    }

    /**
     * chrome.cookies.getAll(details)
     */
    fun getAll(sender: ExtensionSender, details: JSONObject): JSONArray {
        if (!permissionAdapter.hasApiPermission(sender.extensionId, "cookies", sender.isPrivate)) {
            throw SecurityException("SecurityError: Extension does not have 'cookies' permission.")
        }

        if (sender.isPrivate && !permissionAdapter.isAllowedInPrivate(sender.extensionId)) {
            throw SecurityException("SecurityError: Extension not allowed in private browsing context.")
        }

        val url = details.optString("url")
        val domain = details.optString("domain")
        val storeId = if (details.has("storeId")) details.getString("storeId") else null

        validateCookieField(url, "url")
        validateCookieField(domain, "domain")

        if (url.isBlank() && domain.isBlank()) {
            throw IllegalArgumentException("COOKIE_GETALL_UNSUPPORTED: WebView requires target url or domain for cookie query.")
        }

        val targetUrl = when {
            url.isNotBlank() -> url
            domain.isNotBlank() -> if (domain.startsWith("http://") || domain.startsWith("https://")) domain else "https://$domain"
            else -> ""
        }

        if (targetUrl.isNotBlank() && !verifyCookiePermissions(sender, targetUrl)) {
            throw SecurityException("SecurityError: Permission denied or invalid host/URL for cookies.")
        }

        val profileName = resolveTargetProfile(sender, storeId) ?: return JSONArray()
        val cookiesStr = cookieEngine.getCookie(profileName, targetUrl) ?: ""
        if (cookiesStr.isBlank()) return JSONArray()

        val list = JSONArray()
        val host = try { Uri.parse(targetUrl).host ?: domain } catch (e: Exception) { domain }
        val filterName = if (details.has("name")) details.getString("name") else null
        val isPrivateStore = sender.isPrivate || storeId == "1" || storeId == "private" || storeId == "incognito"

        cookiesStr.split(";").forEach {
            val parts = it.split("=", limit = 2)
            val k = parts.getOrNull(0)?.trim() ?: ""
            val v = parts.getOrNull(1)?.trim() ?: ""
            if (k.isNotBlank() && (filterName == null || k == filterName)) {
                if (domain.isBlank() || domainMatches(host, domain)) {
                    list.put(JSONObject().apply {
                        put("name", k)
                        put("value", v)
                        put("domain", host)
                        put("path", (Uri.parse(targetUrl).path ?: "/").ifBlank { "/" })
                        put("storeId", if (isPrivateStore) "1" else "0")
                        put("metadataPartial", true)
                    })
                }
            }
        }
        return list
    }

    /**
     * chrome.cookies.set(details)
     */
    fun set(sender: ExtensionSender, details: JSONObject): JSONObject? {
        val url = details.optString("url")
        val name = details.optString("name")
        val value = details.optString("value", "")
        val storeId = if (details.has("storeId")) details.getString("storeId") else null
        val domain = if (details.has("domain")) details.getString("domain") else null
        val path = if (details.has("path") && details.getString("path").isNotBlank()) details.getString("path") else "/"
        val secure = if (details.has("secure")) details.getBoolean("secure") else url.startsWith("https://", ignoreCase = true)
        val httpOnly = if (details.has("httpOnly")) details.getBoolean("httpOnly") else false
        val sameSite = if (details.has("sameSite")) details.getString("sameSite") else null

        validateCookieField(url, "url")
        validateCookieField(name, "name")
        validateCookieField(value, "value")
        validateCookieField(domain, "domain")
        validateCookieField(path, "path")

        if (!verifyCookiePermissions(sender, url)) {
            throw SecurityException("SecurityError: Permission denied or invalid host/URL for cookies.")
        }

        if (name.isBlank()) return null

        val profileName = resolveTargetProfile(sender, storeId) ?: return null

        val cookieHeader = StringBuilder("$name=$value; Path=$path")
        if (!domain.isNullOrBlank()) {
            cookieHeader.append("; Domain=$domain")
        }
        if (secure) {
            cookieHeader.append("; Secure")
        }
        if (httpOnly) {
            cookieHeader.append("; HttpOnly")
        }
        if (!sameSite.isNullOrBlank()) {
            cookieHeader.append("; SameSite=$sameSite")
        }
        if (details.has("expirationDate")) {
            val exp = details.optDouble("expirationDate", 0.0)
            if (exp > 0.0) {
                val maxAgeSec = (exp - (System.currentTimeMillis() / 1000.0)).toLong()
                if (maxAgeSec > 0) {
                    cookieHeader.append("; Max-Age=$maxAgeSec")
                }
            }
        }

        var successResult = false
        val latch = CountDownLatch(1)

        cookieEngine.setCookie(profileName, url, cookieHeader.toString()) { result ->
            successResult = result
            latch.countDown()
        }

        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            return null
        }

        if (!successResult) {
            return null
        }

        val host = try { Uri.parse(url).host ?: domain ?: "" } catch (e: Exception) { domain ?: "" }
        val isPrivateStore = sender.isPrivate || storeId == "1" || storeId == "private" || storeId == "incognito"
        val createdCookie = JSONObject().apply {
            put("name", name)
            put("value", value)
            put("domain", host)
            put("path", path)
            put("secure", secure)
            put("httpOnly", httpOnly)
            if (!sameSite.isNullOrBlank()) put("sameSite", sameSite)
            put("session", !details.has("expirationDate"))
            put("storeId", if (isPrivateStore) "1" else "0")
            put("hostOnly", domain.isNullOrBlank())
        }

        dispatchCookieOnChanged(url, false, createdCookie, sender, "explicit")

        return createdCookie
    }

    /**
     * chrome.cookies.remove(details)
     */
    fun remove(sender: ExtensionSender, details: JSONObject): JSONObject? {
        val url = details.optString("url")
        val name = details.optString("name")
        val storeId = if (details.has("storeId")) details.getString("storeId") else null

        validateCookieField(url, "url")
        validateCookieField(name, "name")

        if (!verifyCookiePermissions(sender, url)) {
            throw SecurityException("SecurityError: Permission denied or invalid host/URL for cookies.")
        }

        if (name.isBlank()) return null

        val profileName = resolveTargetProfile(sender, storeId) ?: return null

        var successResult = false
        val latch = CountDownLatch(1)

        cookieEngine.removeCookie(profileName, url, name, null, "/") { result ->
            successResult = result
            latch.countDown()
        }

        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            return null
        }

        if (!successResult) {
            return null
        }

        val isPrivateStore = sender.isPrivate || storeId == "1" || storeId == "private" || storeId == "incognito"
        val result = JSONObject().apply {
            put("url", url)
            put("name", name)
            put("storeId", if (isPrivateStore) "1" else "0")
        }

        val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        val deletedCookie = JSONObject().apply {
            put("name", name)
            put("domain", host)
            put("path", "/")
            put("storeId", if (isPrivateStore) "1" else "0")
            put("metadataPartial", true)
        }

        dispatchCookieOnChanged(url, true, deletedCookie, sender, "explicit")

        return result
    }

    /**
     * chrome.cookies.getAllCookieStores()
     */
    fun getAllCookieStores(sender: ExtensionSender): JSONArray {
        if (!permissionAdapter.hasApiPermission(sender.extensionId, "cookies", sender.isPrivate)) {
            throw SecurityException("SecurityError: Extension does not have 'cookies' permission.")
        }

        val normalTabIds = JSONArray()
        val privateTabIds = JSONArray()

        val normalTabs = tabEngine?.getNormalTabs() ?: emptyList()
        val privateTabs = tabEngine?.getPrivateTabs() ?: emptyList()

        for (t in normalTabs) {
            val idInt = t.id.toIntOrNull()
            if (idInt != null) normalTabIds.put(idInt) else normalTabIds.put(t.id)
        }

        for (t in privateTabs) {
            val idInt = t.id.toIntOrNull()
            if (idInt != null) privateTabIds.put(idInt) else privateTabIds.put(t.id)
        }

        val result = JSONArray()
        result.put(JSONObject().apply {
            put("id", "0")
            put("tabIds", normalTabIds)
        })

        if (permissionAdapter.isAllowedInPrivate(sender.extensionId)) {
            result.put(JSONObject().apply {
                put("id", "1")
                put("tabIds", privateTabIds)
            })
        }

        return result
    }

    private fun handleCookieChangeEvent(event: CookieChangeEvent) {
        val targetStoreId = if (event.profileName.startsWith("private_profile_")) "1" else "0"
        val isCookiePrivate = targetStoreId == "1"

        for (ext in registry.getAllActiveExtensions()) {
            val extensionId = ext.id
            if (!permissionAdapter.hasApiPermission(extensionId, "cookies", isCookiePrivate)) {
                continue
            }
            if (isCookiePrivate && !permissionAdapter.isAllowedInPrivate(extensionId)) {
                continue
            }
            if (event.url.isNotBlank() && !permissionAdapter.hasHostPermission(extensionId, event.url, isCookiePrivate)) {
                continue
            }
            eventManager.triggerEventForExtension(extensionId, "cookies.onChanged", JSONObject().apply {
                put("removed", event.removed)
                put("cookie", event.cookieObj)
                put("cause", event.cause)
            })
        }
    }

    /**
     * Dispatches cookies.onChanged event to active extensions with appropriate permissions.
     */
    private fun dispatchCookieOnChanged(
        cookieUrl: String,
        removed: Boolean,
        cookieObj: JSONObject,
        sender: ExtensionSender,
        cause: String = "explicit"
    ) {
        val isCookiePrivate = sender.isPrivate || cookieObj.optString("storeId") == "1"
        for (ext in registry.getAllActiveExtensions()) {
            val extensionId = ext.id
            if (!permissionAdapter.hasApiPermission(extensionId, "cookies", isCookiePrivate)) {
                continue
            }
            if (isCookiePrivate && !permissionAdapter.isAllowedInPrivate(extensionId)) {
                continue
            }
            if (cookieUrl.isNotBlank() && !permissionAdapter.hasHostPermission(extensionId, cookieUrl, isCookiePrivate)) {
                continue
            }
            eventManager.triggerEventForExtension(extensionId, "cookies.onChanged", JSONObject().apply {
                put("removed", removed)
                put("cookie", cookieObj)
                put("cause", cause)
            })
        }
    }
}
