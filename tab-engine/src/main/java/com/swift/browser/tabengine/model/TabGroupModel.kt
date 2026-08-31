package com.swift.browser.tabengine.model

data class TabGroupModel(
    val id: String,
    val name: String = "Group",
    val color: Long = 0xFFCCCCCC,
    val isIncognito: Boolean = false,
    val isPrivate: Boolean = isIncognito,
    val privateSessionId: String? = null,
    val tabs: List<TabModel> = emptyList(),
    val activeTabId: String? = null,
    val isCollapsed: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis(),
    val isSuspended: Boolean = false
)
