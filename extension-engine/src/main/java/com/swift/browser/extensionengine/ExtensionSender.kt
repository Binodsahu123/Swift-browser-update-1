package com.swift.browser.extensionengine

import org.json.JSONObject

enum class ExtensionContextType {
    SERVICE_WORKER,
    BACKGROUND,
    CONTENT_SCRIPT,
    POPUP,
    OPTIONS,
    SIDE_PANEL,
    DEVTOOLS,
    EXTENSION_PAGE,
    WEB_PAGE
}

data class ExtensionSender(
    val extensionId: String,
    val extensionVersion: String? = null,
    val manifestVersion: Int? = 3,
    val tabId: String? = null,
    val windowId: String? = null,
    val frameId: Int? = 0,
    val documentId: String? = null,
    val url: String? = null,
    val origin: String? = null,
    val contextType: ExtensionContextType = ExtensionContextType.BACKGROUND,
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null,
    val enabled: Boolean = true,
    val requiredPermission: String? = null,
    val hostPermission: String? = null
) {
    fun enrichWithExtension(ext: ParsedExtension): ExtensionSender {
        return copy(
            extensionVersion = ext.version,
            manifestVersion = ext.manifestVersion,
            enabled = ext.isEnabled
        )
    }

    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("id", extensionId)
            if (extensionVersion != null) put("version", extensionVersion)
            if (manifestVersion != null) put("manifestVersion", manifestVersion)
            if (url != null) put("url", url)
            if (origin != null) put("origin", origin)
            if (frameId != null) put("frameId", frameId)
            if (documentId != null) put("documentId", documentId)
            put("contextType", contextType.name)
            put("isPrivate", isPrivate)
            if (privateSessionId != null) put("privateSessionId", privateSessionId)
            put("enabled", enabled)
            if (requiredPermission != null) put("requiredPermission", requiredPermission)
            if (hostPermission != null) put("hostPermission", hostPermission)
            if (tabId != null) {
                val numericTabId = TabIdMapper.getIntId(tabId)
                val tabObj = JSONObject().apply {
                    put("id", numericTabId)
                    if (url != null) put("url", url)
                    if (windowId != null) {
                        put("windowId", windowId.toIntOrNull() ?: 1)
                    } else {
                        put("windowId", 1)
                    }
                }
                put("tab", tabObj)
            }
        }
    }
}
