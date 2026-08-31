package com.swift.browser.newsengine.api

import com.swift.browser.newsengine.NewsItemEntity
import com.swift.browser.newsengine.state.NewsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NewsEngineApi {
    val uiState: StateFlow<NewsUiState>
    fun selectCategory(category: String)
    fun refresh()
    fun getNewsFlow(): Flow<List<NewsItemEntity>>
    fun getFeedUrlForCategory(category: String): String
}
