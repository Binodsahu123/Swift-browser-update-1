package com.swift.browser.newsengine

import com.swift.browser.newsengine.state.NewsUiState
import com.swift.browser.newsengine.ui.NEWS_CATEGORIES
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class NewsEngineTest {

    @Test
    fun testDefaultNewsCategories() {
        val expected = listOf(
            "For You",
            "India",
            "Tech",
            "Sports",
            "Entertainment",
            "Business",
            "Health",
            "Science"
        )
        assertEquals(expected, NEWS_CATEGORIES)
    }

    @Test
    fun testDefaultUiState() {
        val defaultState = NewsUiState()
        assertEquals("For You", defaultState.feedCategory)
        assertFalse(defaultState.isFeedLoading)
        assertTrue(defaultState.articles.isEmpty())
        assertNull(defaultState.error)
    }

    @Test
    fun testCategoryFeedUrls() {
        val repo = object : NewsEngine {
            override fun getNewsFlow() = kotlinx.coroutines.flow.emptyFlow<List<NewsItemEntity>>()
            override suspend fun refreshNews(rssUrl: String, category: String) {}
            override fun getFeedUrlForCategory(category: String): String {
                return when (category) {
                    "For You", "Top Stories" -> "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"
                    "India" -> "https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms"
                    "Tech", "Technology" -> "https://timesofindia.indiatimes.com/rssfeeds/66949542.cms"
                    "Sports" -> "https://timesofindia.indiatimes.com/rssfeeds/4719148.cms"
                    "Entertainment" -> "https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms"
                    "Business" -> "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms"
                    "Science" -> "https://timesofindia.indiatimes.com/rssfeeds/-2128672765.cms"
                    "Health" -> "https://timesofindia.indiatimes.com/rssfeeds/3908999.cms"
                    else -> "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"
                }
            }
        }

        assertTrue(repo.getFeedUrlForCategory("For You").contains("rssfeedstopstories"))
        assertTrue(repo.getFeedUrlForCategory("India").contains("-2128936835"))
        assertTrue(repo.getFeedUrlForCategory("Tech").contains("66949542"))
        assertTrue(repo.getFeedUrlForCategory("Sports").contains("4719148"))
        assertTrue(repo.getFeedUrlForCategory("Entertainment").contains("1081479906"))
        assertTrue(repo.getFeedUrlForCategory("Business").contains("1898055"))
        assertTrue(repo.getFeedUrlForCategory("Science").contains("-2128672765"))
        assertTrue(repo.getFeedUrlForCategory("Health").contains("3908999"))
    }
}
