package com.swift.browser.browserengine

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FindInPageState(
    val isActive: Boolean = false,
    val query: String = "",
    val currentMatch: Int = 0,
    val totalMatches: Int = 0
)

class FindInPageEngine {
    private val _state = MutableStateFlow(FindInPageState())
    val state: StateFlow<FindInPageState> = _state.asStateFlow()

    fun toggleFindInPage(active: Boolean, webView: WebView?) {
        if (!active) {
            webView?.clearMatches()
            _state.update {
                it.copy(
                    isActive = false,
                    query = "",
                    currentMatch = 0,
                    totalMatches = 0
                )
            }
        } else {
            _state.update { it.copy(isActive = true) }
            setupFindListener(webView)
        }
    }

    fun setupFindListener(webView: WebView?) {
        webView?.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                _state.update {
                    it.copy(
                        currentMatch = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                        totalMatches = numberOfMatches
                    )
                }
            }
        }
    }

    fun search(query: String, webView: WebView?) {
        _state.update { it.copy(query = query) }
        webView?.findAllAsync(query)
    }

    fun findNext(forward: Boolean = true, webView: WebView?) {
        webView?.findNext(forward)
    }

    fun clear(webView: WebView?) {
        webView?.clearMatches()
        _state.update {
            it.copy(
                isActive = false,
                query = "",
                currentMatch = 0,
                totalMatches = 0
            )
        }
    }

    companion object {
        @Volatile
        private var instance: FindInPageEngine? = null

        fun getInstance(): FindInPageEngine {
            return instance ?: synchronized(this) {
                instance ?: FindInPageEngine().also { instance = it }
            }
        }
    }
}
