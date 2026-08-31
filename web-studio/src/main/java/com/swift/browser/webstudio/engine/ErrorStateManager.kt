package com.swift.browser.webstudio.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ErrorStateManager {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setError(message: String?) {
        _error.value = message
    }
    
    fun clearError() {
        _error.value = null
    }
}
