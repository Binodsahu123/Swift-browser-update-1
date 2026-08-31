package com.swift.browser.extensionengine

data class ExtensionRuntimeContext(
    val contextType: ExtensionContextType,
    val extensionId: String,
    val tabId: String? = null,
    val windowId: String? = null,
    val frameId: Int? = null,
    val documentId: String? = null,
    val url: String? = null,
    val origin: String? = null,
    val privateSessionId: String? = null,
    val runtimeGenerationId: String? = null
)
