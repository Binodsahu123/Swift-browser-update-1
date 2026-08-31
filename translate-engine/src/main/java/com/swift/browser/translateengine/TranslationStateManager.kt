package com.swift.browser.translateengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TranslationState {
    Hidden,
    Visible,
    Translating,
    Translated,
    Original
}

class TranslationStateManager {
    private val _currentState = MutableStateFlow(TranslationState.Hidden)
    val currentState: StateFlow<TranslationState> = _currentState.asStateFlow()

    fun getState(): TranslationState {
        return _currentState.value
    }

    fun transitionTo(state: TranslationState) {
        _currentState.value = state
    }
}
