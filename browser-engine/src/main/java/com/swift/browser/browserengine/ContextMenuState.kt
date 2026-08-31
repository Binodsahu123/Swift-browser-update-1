package com.swift.browser.browserengine

data class ContextMenuState(
    val show: Boolean = false,
    val url: String = "",
    val isImage: Boolean = false,
    val isImageLink: Boolean = false,
    val imageUrl: String = "",
    val tabId: String? = null
)
