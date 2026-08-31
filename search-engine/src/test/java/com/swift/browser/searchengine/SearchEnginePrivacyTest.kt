package com.swift.browser.searchengine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEnginePrivacyTest {

    private lateinit var searchEngine: SearchEngineImpl
    private lateinit var provider: SearchSuggestionsProvider

    @Before
    fun setUp() {
        searchEngine = SearchEngineImpl()
        provider = SearchSuggestionsProvider(searchEngine)
    }

    @Test
    fun testPrivateSearchDoesNotModifySuggestionCache() {
        val normalHistory = listOf(
            SearchSuggestion(SuggestionType.HISTORY, "Secret History Search", "https://secret-example.com")
        )
        val normalBookmark = listOf(
            SearchSuggestion(SuggestionType.BOOKMARK, "Saved Bookmark", "https://bookmark.com")
        )

        // Request suggestions in private mode
        val privateSuggestions = searchEngine.getSearchSuggestions(
            input = "Secret",
            engineName = "Google",
            historyResults = normalHistory,
            bookmarkResults = normalBookmark,
            client = OkHttpClient(),
            browsingContext = BrowsingContext.PRIVATE
        )

        // Ensure private search did not cache suggestions into persistent memory cache
        // Querying again with normal context should not return the private query cached state
        val normalEmptyHistorySuggestions = searchEngine.getSearchSuggestions(
            input = "Secret",
            engineName = "Google",
            historyResults = emptyList(),
            bookmarkResults = emptyList(),
            client = OkHttpClient(),
            browsingContext = BrowsingContext.NORMAL
        )

        // Since history was empty and nothing was cached from private session, result should be empty (no network during test)
        assertTrue(normalEmptyHistorySuggestions.none { it.url == "https://secret-example.com" })
    }

    @Test
    fun testSearchSuggestionsProviderSuppressesHistoryInPrivateContext() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val historyList = listOf(
            SearchSuggestion(SuggestionType.HISTORY, "Bank Account Portal", "https://mybank.com")
        )
        val bookmarkList = listOf(
            SearchSuggestion(SuggestionType.BOOKMARK, "Kotlin Docs", "https://kotlinlang.org")
        )

        // 1. Fetch suggestions in PRIVATE mode
        provider.fetchSuggestions(
            query = "Bank",
            engineName = "Google",
            historyResults = historyList,
            bookmarkResults = bookmarkList,
            coroutineScope = testScope,
            browsingContext = BrowsingContext.PRIVATE
        )

        val privateResults = provider.suggestions.value
        // Private suggestions MUST NOT contain history items
        assertTrue("Private search suggestion must not expose history", privateResults.none { it.type == SuggestionType.HISTORY })
        assertTrue("Private search suggestion must not expose secret history URL", privateResults.none { it.url == "https://mybank.com" })

        // 2. Fetch suggestions in NORMAL mode
        provider.fetchSuggestions(
            query = "Bank",
            engineName = "Google",
            historyResults = historyList,
            bookmarkResults = bookmarkList,
            coroutineScope = testScope,
            browsingContext = BrowsingContext.NORMAL
        )

        val normalResults = provider.suggestions.value
        // Normal suggestions should include history
        assertTrue("Normal search suggestions include history", normalResults.any { it.title == "Bank Account Portal" })
    }

    @Test
    fun testBookmarksRemainAllowedInPrivateContext() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val bookmarkList = listOf(
            SearchSuggestion(SuggestionType.BOOKMARK, "Important Document", "https://docs.example.com")
        )

        // Fetch in private mode
        provider.fetchSuggestions(
            query = "Important",
            engineName = "Google",
            historyResults = emptyList(),
            bookmarkResults = bookmarkList,
            coroutineScope = testScope,
            browsingContext = BrowsingContext.PRIVATE
        )

        val privateResults = provider.suggestions.value
        assertTrue("Explicit bookmarks remain accessible in private mode", privateResults.any { it.title == "Important Document" })
    }
}
