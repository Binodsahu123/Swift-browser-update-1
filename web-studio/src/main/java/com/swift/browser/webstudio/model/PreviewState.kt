package com.swift.browser.webstudio.model

data class PreviewState(
    val isPreviewing: Boolean = false,
    val previewUrl: String = "",
    val rawHtml: String? = null,
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)
