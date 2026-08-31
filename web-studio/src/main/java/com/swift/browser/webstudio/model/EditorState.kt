package com.swift.browser.webstudio.model

import com.swift.browser.webstudio.EditorTab

data class EditorState(
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabId: String? = null,
    val theme: String = "Dark",
    val fontSize: Int = 14,
    val wordWrap: Boolean = true
)
