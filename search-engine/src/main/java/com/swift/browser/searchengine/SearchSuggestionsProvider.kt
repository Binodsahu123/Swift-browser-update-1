package com.swift.browser.searchengine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SearchSuggestionsProvider(
    private val searchEngine: SearchEngine,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions.asStateFlow()

    private var searchJob: Job? = null

    fun fetchSuggestions(
        query: String,
        engineName: String,
        historyResults: List<SearchSuggestion> = emptyList(),
        bookmarkResults: List<SearchSuggestion> = emptyList(),
        coroutineScope: CoroutineScope,
        browsingContext: BrowsingContext = BrowsingContext.NORMAL
    ) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        
        // When in private browsing, history results from persistent storage must not be shown/suggested
        val effectiveHistoryResults = if (browsingContext.isPrivate) emptyList() else historyResults

        // Immediate update with local results
        val localResults = (effectiveHistoryResults + bookmarkResults).filter { 
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) 
        }
        _suggestions.value = localResults.take(9)

        searchJob = coroutineScope.launch {
            delay(300) // Debounce network requests
            val results = withContext(Dispatchers.IO) {
                searchEngine.getSearchSuggestions(
                    input = query,
                    engineName = engineName,
                    historyResults = effectiveHistoryResults,
                    bookmarkResults = bookmarkResults,
                    client = client,
                    browsingContext = browsingContext
                )
            }
            _suggestions.value = results
        }
    }
    
    fun clear() {
        searchJob?.cancel()
        _suggestions.value = emptyList()
    }
}
