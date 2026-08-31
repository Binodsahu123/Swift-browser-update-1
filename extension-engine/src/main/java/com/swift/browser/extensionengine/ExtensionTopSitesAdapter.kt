package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject

class ExtensionTopSitesAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry
) {
    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (!permissionManager.hasApiPermission(extId, ext.permissions, "topSites")) {
            throw SecurityException("SecurityError: Extension does not have 'topSites' permission")
        }
        return ext
    }

    fun get(sender: ExtensionSender, delegate: BrowserDelegate?): JSONArray {
        validate(sender)
        val result = JSONArray()
        // Read top sites from browser delegate query or default top sites
        val topSites = try {
            val queryResult = delegate?.queryTabs(JSONObject().put("topSites", true))
            if (queryResult != null && queryResult.length() > 0) {
                queryResult
            } else {
                JSONArray().apply {
                    put(JSONObject().put("url", "https://www.google.com").put("title", "Google"))
                    put(JSONObject().put("url", "https://www.wikipedia.org").put("title", "Wikipedia"))
                    put(JSONObject().put("url", "https://github.com").put("title", "GitHub"))
                }
            }
        } catch (e: Exception) {
            JSONArray().apply {
                put(JSONObject().put("url", "https://www.google.com").put("title", "Google"))
            }
        }
        return topSites
    }
}
