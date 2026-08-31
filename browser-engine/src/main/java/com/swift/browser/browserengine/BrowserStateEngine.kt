package com.swift.browser.browserengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrowserStateEngine {
    private val _browserState = MutableStateFlow(BrowserState.IDLE)
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    private val _pageState = MutableStateFlow(BrowserPageState())
    val pageState: StateFlow<BrowserPageState> = _pageState.asStateFlow()

    private val _navigationState = MutableStateFlow(BrowserNavigationState())
    val navigationState: StateFlow<BrowserNavigationState> = _navigationState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _favicon = MutableStateFlow<String?>(null)
    val favicon: StateFlow<String?> = _favicon.asStateFlow()

    private val _browserError = MutableStateFlow<BrowserError?>(null)
    val browserError: StateFlow<BrowserError?> = _browserError.asStateFlow()

    private val _diagnostics = MutableStateFlow("Engine initialized")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    fun updateState(state: BrowserState) {
        _browserState.value = state
    }

    fun updateUrl(newUrl: String) {
        _url.value = newUrl
        _pageState.value = _pageState.value.copy(url = newUrl)
    }

    fun updateTitle(newTitle: String) {
        _title.value = newTitle
        _pageState.value = _pageState.value.copy(title = newTitle)
    }

    fun updateFavicon(iconUrl: String?) {
        _favicon.value = iconUrl
        _pageState.value = _pageState.value.copy(favicon = iconUrl)
    }

    fun updateLoading(loading: Boolean, progress: Int = 0) {
        _isLoading.value = loading
        _pageState.value = _pageState.value.copy(isLoading = loading, loadingProgress = progress)
        if (loading) {
            _browserState.value = BrowserState.LOADING
        } else if (_browserError.value == null) {
            _browserState.value = BrowserState.READY
        }
    }

    fun updateNavigation(canBack: Boolean, canForward: Boolean) {
        _navigationState.value = BrowserNavigationState(canGoBack = canBack, canGoForward = canForward)
    }

    fun setError(error: BrowserError?) {
        _browserError.value = error
        if (error != null) {
            _browserState.value = BrowserState.ERROR
        }
    }

    fun updateDiagnostics(msg: String) {
        _diagnostics.value = msg
    }

    fun updateIncognitoMode(incognito: Boolean) {
        _pageState.value = _pageState.value.copy(isIncognito = incognito)
    }

    fun updateUserAgentMode(mode: String) {
        _pageState.value = _pageState.value.copy(userAgentMode = mode)
    }
}
