package com.swift.browser.newsengine.state

import com.swift.browser.newsengine.NewsItemEntity

data class NewsUiState(
    val feedCategory: String = "For You",
    val isFeedLoading: Boolean = false,
    val articles: List<NewsItemEntity> = emptyList(),
    val error: String? = null,
    val lastRefresh: Long = 0L
)
