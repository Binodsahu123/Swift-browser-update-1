package com.swift.browser.webstudio.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoadingStateManager {
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage = _loadingMessage.asStateFlow()

    fun setLoading(loading: Boolean, message: String = "") {
        _isLoading.value = loading
        _loadingMessage.value = message
    }
}
