package com.swift.browser.notificationengine.data

import android.content.Context
import com.swift.browser.notificationengine.NotificationDatabase
import com.swift.browser.notificationengine.NotificationHistoryItem
import com.swift.browser.notificationengine.NotificationSubscription
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing clean, structured data access for Notification Engine entities.
 */
class NotificationRepository(context: Context) {
    private val db = NotificationDatabase.getDatabase(context)
    private val subscriptionDao = db.subscriptionDao()
    private val historyDao = db.historyDao()

    fun getAllSubscriptionsFlow(): Flow<List<NotificationSubscription>> =
        subscriptionDao.getAllSubscriptionsFlow()

    suspend fun getAllSubscriptions(): List<NotificationSubscription> =
        subscriptionDao.getAllSubscriptions()

    suspend fun getSubscription(url: String): NotificationSubscription? =
        subscriptionDao.getSubscription(url)

    suspend fun saveSubscription(subscription: NotificationSubscription) {
        subscriptionDao.insertSubscription(subscription)
    }

    suspend fun deleteSubscription(url: String) {
        subscriptionDao.deleteSubscriptionByUrl(url)
    }

    fun getAllHistoryFlow(): Flow<List<NotificationHistoryItem>> =
        historyDao.getAllHistoryFlow()

    suspend fun getAllHistory(): List<NotificationHistoryItem> =
        historyDao.getAllHistory()

    suspend fun insertHistory(item: NotificationHistoryItem) {
        historyDao.insertHistoryItem(item)
    }

    suspend fun markAsRead(id: Int) {
        historyDao.markAsRead(id)
    }

    suspend fun markAllAsRead() {
        historyDao.markAllAsRead()
    }

    suspend fun deleteHistory(id: Int) {
        historyDao.deleteHistoryItem(id)
    }

    suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }
}
