package com.swift.browser.notificationengine

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList

class NotificationHistoryManager(private val context: Context) {
    private val repository = com.swift.browser.notificationengine.data.NotificationRepository(context)

    companion object {
        // Runtime/session-only in-memory storage for private browsing notifications.
        // Never written to Room SQLite database to protect user privacy.
        private val privateHistoryList = CopyOnWriteArrayList<NotificationHistoryItem>()
    }

    fun getAllHistoryFlow(): Flow<List<NotificationHistoryItem>> {
        return repository.getAllHistoryFlow()
    }

    suspend fun getAllHistoryList(): List<NotificationHistoryItem> {
        return repository.getAllHistory()
    }

    fun getPrivateHistoryList(): List<NotificationHistoryItem> {
        return privateHistoryList.toList()
    }

    fun getHistoryForWebsite(websiteUrl: String): Flow<List<NotificationHistoryItem>> {
        return repository.getAllHistoryFlow().map { list -> list.filter { it.websiteUrl == websiteUrl } }
    }

    suspend fun saveHistory(
        websiteUrl: String,
        websiteName: String,
        title: String,
        body: String,
        clickUrl: String,
        browsingContext: NotificationBrowsingContext = NotificationBrowsingContext.NORMAL
    ) {
        addHistoryItem(websiteUrl, websiteName, title, body, clickUrl, browsingContext)
    }

    suspend fun addHistoryItem(
        websiteUrl: String,
        websiteName: String,
        title: String,
        body: String,
        clickUrl: String,
        browsingContext: NotificationBrowsingContext = NotificationBrowsingContext.NORMAL
    ) {
        val item = NotificationHistoryItem(
            id = if (browsingContext.isPrivate) (System.currentTimeMillis() % Int.MAX_VALUE).toInt() else 0,
            websiteUrl = websiteUrl,
            websiteName = websiteName,
            title = title,
            body = body,
            clickUrl = clickUrl,
            timestamp = System.currentTimeMillis()
        )

        if (browsingContext.isPrivate) {
            // Private browsing: runtime/in-memory only. NEVER persist to disk/database.
            privateHistoryList.add(0, item)
        } else {
            // Normal browsing: persist to Room database
            repository.insertHistory(item)
        }
    }

    fun clearPrivateHistory(sessionId: String? = null) {
        // Purge private in-memory history when private session closes
        privateHistoryList.clear()
    }

    suspend fun markAsRead(id: Int) {
        val inMemory = privateHistoryList.indexOfFirst { it.id == id }
        if (inMemory != -1) {
            val item = privateHistoryList[inMemory]
            privateHistoryList[inMemory] = item.copy(isRead = true)
        }
        repository.markAsRead(id)
    }

    suspend fun markAllAsRead() {
        for (i in 0 until privateHistoryList.size) {
            privateHistoryList[i] = privateHistoryList[i].copy(isRead = true)
        }
        repository.markAllAsRead()
    }

    suspend fun deleteItem(id: Int) {
        privateHistoryList.removeAll { it.id == id }
        repository.deleteHistory(id)
    }

    suspend fun clearHistory() {
        repository.clearHistory()
    }

    /**
     * Filters a list of history items by standard intervals:
     * - "today"
     * - "yesterday"
     * - "last_7_days"
     * - "all"
     */
    fun filterHistory(items: List<NotificationHistoryItem>, ageFilter: String): List<NotificationHistoryItem> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        // Start of yesterday
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startOfYesterday = calendar.timeInMillis

        // Start of last 7 days
        calendar.add(Calendar.DAY_OF_YEAR, -5) // Already back 1 day, subtract 5 more to hit 7 days total
        val startOfLast7Days = calendar.timeInMillis

        return when (ageFilter) {
            "today" -> items.filter { it.timestamp >= startOfToday }
            "yesterday" -> items.filter { it.timestamp in startOfYesterday until startOfToday }
            "last_7_days" -> items.filter { it.timestamp >= startOfLast7Days }
            else -> items
        }
    }

    /**
     * Searches notification logs for matching title/body or website name.
     */
    fun searchHistory(items: List<NotificationHistoryItem>, query: String): List<NotificationHistoryItem> {
        if (query.isBlank()) return items
        val lower = query.lowercase().trim()
        return items.filter {
            it.title.lowercase().contains(lower) ||
            it.body.lowercase().contains(lower) ||
            it.websiteName.lowercase().contains(lower)
        }
    }
}
