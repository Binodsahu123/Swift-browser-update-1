package com.swift.browser.historyengine.api

import com.swift.browser.historyengine.BrowsingContext
import com.swift.browser.historyengine.HistoryDao
import com.swift.browser.historyengine.HistoryEngine
import com.swift.browser.historyengine.HistoryItem
import com.swift.browser.historyengine.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HistoryEngineProvider {
    @Volatile
    private var instance: HistoryEngine? = null

    fun getEngine(historyDao: HistoryDao): HistoryEngine {
        return instance ?: synchronized(this) {
            instance ?: HistoryRepository(historyDao).also { instance = it }
        }
    }

    val api: HistoryEngine
        get() = instance ?: object : HistoryEngine {
            private val _hist = MutableStateFlow<List<HistoryItem>>(emptyList())
            override fun getHistoryFlow() = _hist.asStateFlow()
            override fun getRecentHistory(limit: Int) = _hist.asStateFlow()
            override suspend fun addHistoryItem(url: String, title: String) {}
            override suspend fun addHistoryItem(url: String, title: String, browsingContext: BrowsingContext) {}
            override suspend fun clearAllHistory() { _hist.value = emptyList() }
            override suspend fun deleteHistoryItem(id: Int) { _hist.value = _hist.value.filterNot { it.id == id } }
            override suspend fun deleteHistorySince(timestamp: Long) {}
            override suspend fun deleteHistoryRange(startTime: Long, endTime: Long) {}
            override suspend fun queryHistory(query: String) = emptyList<HistoryItem>()
            override suspend fun queryHistory(query: String, limit: Int) = emptyList<HistoryItem>()
            override suspend fun queryHistorySemantic(query: String) = emptyList<HistoryItem>()
        }
}
