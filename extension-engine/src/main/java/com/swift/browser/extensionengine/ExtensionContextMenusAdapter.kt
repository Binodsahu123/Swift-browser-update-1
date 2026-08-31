package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ContextMenuItem(
    val id: String,
    val extensionId: String,
    var title: String,
    var contexts: List<String>,
    var type: String,
    var checked: Boolean,
    var parentId: String?,
    var targetUrlPatterns: List<String>,
    var enabled: Boolean,
    var visible: Boolean = true
)

class ExtensionContextMenusAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    companion object {
        val itemsMap = ConcurrentHashMap<String, ContextMenuItem>() // key: "extId_itemId"
        private val autoIdGenerator = AtomicInteger(1)

        fun cleanupExtensionState(extensionId: String) {
            val keysToRemove = itemsMap.keys().toList().filter { it.startsWith("${extensionId}_") }
            for (k in keysToRemove) {
                itemsMap.remove(k)
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
        if (!permissionManager.hasApiPermission(extId, ext.permissions, "contextMenus")) {
            throw SecurityException("SecurityError: Extension does not have 'contextMenus' permission")
        }
        return ext
    }

    fun create(sender: ExtensionSender, createProperties: JSONObject): JSONObject {
        val ext = validate(sender)
        
        val itemId = if (createProperties.has("id")) {
            createProperties.getString("id")
        } else {
            "item_${autoIdGenerator.getAndIncrement()}"
        }

        val key = "${ext.id}_$itemId"
        if (itemsMap.containsKey(key)) {
            throw IllegalArgumentException("CONTEXT_MENU_DUPLICATE_ID")
        }

        val title = createProperties.optString("title", "")
        val type = createProperties.optString("type", "normal")
        val checked = createProperties.optBoolean("checked", false)
        val parentId = if (createProperties.has("parentId")) createProperties.getString("parentId") else null
        val enabled = createProperties.optBoolean("enabled", true)
        val visible = createProperties.optBoolean("visible", true)

        val supportedTypes = listOf("normal", "checkbox", "radio", "separator")
        if (type.isNotBlank() && !supportedTypes.contains(type.lowercase())) {
            throw IllegalArgumentException("CONTEXT_MENU_UNSUPPORTED")
        }

        if (parentId != null) {
            val parentKey = "${ext.id}_$parentId"
            if (!itemsMap.containsKey(parentKey)) {
                throw IllegalArgumentException("CONTEXT_MENU_NOT_FOUND")
            }
        }

        val contextsArr = createProperties.optJSONArray("contexts")
        val contexts = ArrayList<String>()
        val validContexts = listOf("all", "page", "selection", "link", "editable", "image", "video", "audio", "frame")
        if (contextsArr != null) {
            for (i in 0 until contextsArr.length()) {
                val ctx = contextsArr.getString(i).lowercase()
                if (!validContexts.contains(ctx)) {
                    throw IllegalArgumentException("CONTEXT_MENU_CONTEXT_UNAVAILABLE")
                }
                contexts.add(ctx)
            }
        } else {
            contexts.add("page")
        }

        val patternsArr = createProperties.optJSONArray("targetUrlPatterns")
        val patterns = ArrayList<String>()
        if (patternsArr != null) {
            for (i in 0 until patternsArr.length()) {
                patterns.add(patternsArr.getString(i))
            }
        }

        val item = ContextMenuItem(
            id = itemId,
            extensionId = ext.id,
            title = title,
            contexts = contexts,
            type = type,
            checked = checked,
            parentId = parentId,
            targetUrlPatterns = patterns,
            enabled = enabled,
            visible = visible
        )
        itemsMap[key] = item
        return JSONObject().put("status", "success").put("id", itemId)
    }

    fun update(sender: ExtensionSender, id: String, updateProperties: JSONObject): JSONObject {
        val ext = validate(sender)
        val key = "${ext.id}_$id"
        val existing = itemsMap[key] ?: throw IllegalArgumentException("CONTEXT_MENU_NOT_FOUND")

        if (updateProperties.has("title")) existing.title = updateProperties.getString("title")
        if (updateProperties.has("type")) {
            val t = updateProperties.getString("type")
            val supportedTypes = listOf("normal", "checkbox", "radio", "separator")
            if (!supportedTypes.contains(t.lowercase())) {
                throw IllegalArgumentException("CONTEXT_MENU_UNSUPPORTED")
            }
            existing.type = t
        }
        if (updateProperties.has("checked")) existing.checked = updateProperties.getBoolean("checked")
        if (updateProperties.has("enabled")) existing.enabled = updateProperties.getBoolean("enabled")
        if (updateProperties.has("visible")) existing.visible = updateProperties.getBoolean("visible")
        if (updateProperties.has("parentId")) {
            val pId = updateProperties.optString("parentId", null)
            if (pId != null) {
                val parentKey = "${ext.id}_$pId"
                if (!itemsMap.containsKey(parentKey)) {
                    throw IllegalArgumentException("CONTEXT_MENU_NOT_FOUND")
                }
            }
            existing.parentId = pId
        }

        return JSONObject().put("status", "success")
    }

    fun remove(sender: ExtensionSender, menuItemId: String): JSONObject {
        val ext = validate(sender)
        val key = "${ext.id}_$menuItemId"
        if (!itemsMap.containsKey(key)) {
            throw IllegalArgumentException("CONTEXT_MENU_NOT_FOUND")
        }
        itemsMap.remove(key)
        return JSONObject().put("status", "success")
    }

    fun removeAll(sender: ExtensionSender): JSONObject {
        val ext = validate(sender)
        val keysToRemove = itemsMap.keys().toList().filter { it.startsWith("${ext.id}_") }
        for (k in keysToRemove) {
            itemsMap.remove(k)
        }
        return JSONObject().put("status", "success")
    }

    fun triggerClick(
        extensionId: String,
        menuItemId: String,
        pageUrl: String? = null,
        tabId: String? = null,
        selectionText: String? = null,
        linkUrl: String? = null,
        srcUrl: String? = null
    ) {
        val ext = registry.getExtension(extensionId) ?: return
        if (!registry.isExtensionEnabled(extensionId)) return

        val info = JSONObject().apply {
            put("menuItemId", menuItemId)
            put("parentMenuItemId", itemsMap["${extensionId}_$menuItemId"]?.parentId ?: JSONObject.NULL)
            put("pageUrl", pageUrl ?: JSONObject.NULL)
            put("selectionText", selectionText ?: JSONObject.NULL)
            put("linkUrl", linkUrl ?: JSONObject.NULL)
            put("srcUrl", srcUrl ?: JSONObject.NULL)
            put("frameUrl", JSONObject.NULL)
            put("editable", false)
            put("mediaType", JSONObject.NULL)
        }

        val tab = JSONObject().apply {
            val intId = if (!tabId.isNullOrBlank()) TabIdMapper.getIntId(tabId) else JSONObject.NULL
            put("id", intId)
            pageUrl?.let { put("url", it) }
        }

        eventManager.triggerEvent("contextMenus.onClicked", JSONObject().apply {
            put("info", info)
            put("tab", tab)
        })
    }
}
