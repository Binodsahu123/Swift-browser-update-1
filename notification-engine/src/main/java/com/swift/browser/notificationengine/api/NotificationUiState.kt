package com.swift.browser.notificationengine.api

import com.swift.browser.notificationengine.NotificationHistoryItem
import com.swift.browser.notificationengine.NotificationSubscription

data class NotificationUiState(
    val centerOpen: Boolean = false,
    val selectedTab: Int = 0,
    val history: List<NotificationHistoryItem> = emptyList(),
    val subscriptions: List<NotificationSubscription> = emptyList(),
    val permissionRequest: String? = null,
    val searchQuery: String = "",
    val filters: String = "",
    val engineStatus: String = "ACTIVE",
    val unreadCount: Int = 0,
    val syncState: String = "IDLE",
    val error: String? = null
)

data class NotificationClickEvent(
    val notificationId: Int,
    val websiteUrl: String,
    val clickUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
