package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class OmniboxSuggestion(
    val content: String,
    val description: String,
    val deletable: Boolean = false
)

class ExtensionOmniboxAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    companion object {
        val registeredKeywords = ConcurrentHashMap<String, String>() // keyword -> extensionId
        val defaultSuggestions = ConcurrentHashMap<String, String>() // extensionId -> description

        fun cleanupExtensionState(extensionId: String) {
            defaultSuggestions.remove(extensionId)
            val keysToRemove = registeredKeywords.filterValues { it == extensionId }.keys
            for (k in keysToRemove) {
                registeredKeywords.remove(k)
            }
        }
    }

    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw IllegalArgumentException("EXTENSION_NOT_FOUND")
        if (!registry.isExtensionEnabled(extId)) {
            throw IllegalArgumentException("EXTENSION_DISABLED")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw IllegalArgumentException("PRIVATE_MODE_DENIED")
        }
        return ext
    }

    private fun getOmniboxKeyword(ext: ParsedExtension): String? {
        try {
            val root = JSONObject(ext.manifestJson)
            if (root.has("omnibox")) {
                val omniObj = root.getJSONObject("omnibox")
                return omniObj.optString("keyword", null)
            }
        } catch (e: Exception) {}
        return null
    }

    fun registerKeyword(sender: ExtensionSender): JSONObject {
        val ext = validate(sender)
        val keyword = getOmniboxKeyword(ext) ?: return JSONObject().put("status", "no_keyword")
        
        if (keyword.isBlank()) {
            throw IllegalArgumentException("OMNIBOX_INVALID")
        }
        // Check alphanumeric
        if (!keyword.matches(Regex("^[a-zA-Z0-9]+$"))) {
            throw IllegalArgumentException("OMNIBOX_INVALID")
        }

        val existingOwner = registeredKeywords[keyword]
        if (existingOwner != null && existingOwner != ext.id) {
            throw IllegalArgumentException("OMNIBOX_KEYWORD_CONFLICT")
        }

        registeredKeywords[keyword] = ext.id
        return JSONObject().put("status", "success").put("keyword", keyword)
    }

    fun setDefaultSuggestion(sender: ExtensionSender, details: JSONObject): JSONObject {
        val ext = validate(sender)
        val description = details.optString("description", "")
        if (description.isBlank()) {
            throw IllegalArgumentException("OMNIBOX_INVALID")
        }
        defaultSuggestions[ext.id] = description
        return JSONObject().put("status", "success")
    }

    fun suggest(sender: ExtensionSender, callbackId: String, suggestions: JSONArray): JSONObject {
        val ext = validate(sender)
        // Verify suggestions properties
        for (i in 0 until suggestions.length()) {
            val s = suggestions.getJSONObject(i)
            if (!s.has("content") || !s.has("description")) {
                throw IllegalArgumentException("OMNIBOX_INVALID")
            }
        }
        // Since custom rendering in Android's search bar from extension is partially supported:
        throw IllegalArgumentException("OMNIBOX_PARTIAL")
    }

    // Event Trigger flows
    fun triggerInputStarted(extensionId: String) {
        val ext = registry.getExtension(extensionId) ?: return
        if (!registry.isExtensionEnabled(extensionId)) return
        eventManager.triggerEvent("omnibox.onInputStarted", JSONObject())
    }

    fun triggerInputChanged(extensionId: String, text: String) {
        val ext = registry.getExtension(extensionId) ?: return
        if (!registry.isExtensionEnabled(extensionId)) return
        val obj = JSONObject().apply {
            put("text", text)
        }
        eventManager.triggerEvent("omnibox.onInputChanged", obj)
    }

    fun triggerInputEntered(extensionId: String, text: String, disposition: String) {
        val ext = registry.getExtension(extensionId) ?: return
        if (!registry.isExtensionEnabled(extensionId)) return
        val obj = JSONObject().apply {
            put("text", text)
            put("disposition", disposition)
        }
        eventManager.triggerEvent("omnibox.onInputEntered", obj)
    }

    fun triggerInputCancelled(extensionId: String) {
        val ext = registry.getExtension(extensionId) ?: return
        if (!registry.isExtensionEnabled(extensionId)) return
        eventManager.triggerEvent("omnibox.onInputCancelled", JSONObject())
    }
}
