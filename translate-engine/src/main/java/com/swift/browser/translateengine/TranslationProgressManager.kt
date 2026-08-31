package com.swift.browser.translateengine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProgressState {
    Idle,
    Translating,
    Completed,
    Failed
}

class TranslationProgressManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _state = MutableStateFlow(ProgressState.Idle)
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    private val _totalNodes = MutableStateFlow(0)
    val totalNodes: StateFlow<Int> = _totalNodes.asStateFlow()

    private val _translatedNodes = MutableStateFlow(0)
    val translatedNodes: StateFlow<Int> = _translatedNodes.asStateFlow()

    private val _remainingNodes = MutableStateFlow(0)
    val remainingNodes: StateFlow<Int> = _remainingNodes.asStateFlow()

    private var completionJob: Job? = null

    fun reset() {
        completionJob?.cancel()
        _state.value = ProgressState.Idle
        _totalNodes.value = 0
        _translatedNodes.value = 0
        _remainingNodes.value = 0
    }

    fun startTranslation(total: Int) {
        completionJob?.cancel()
        _totalNodes.value = total
        _translatedNodes.value = 0
        _remainingNodes.value = total
        _state.value = ProgressState.Translating
    }

    fun updateProgress(translated: Int) {
        _translatedNodes.value = translated
        val total = _totalNodes.value
        if (total > 0) {
            val remaining = total - translated
            _remainingNodes.value = if (remaining > 0) remaining else 0
        } else {
            _remainingNodes.value = 0
        }
    }

    fun setTotal(total: Int) {
        _totalNodes.value = total
        updateProgress(_translatedNodes.value)
    }

    fun completeTranslation() {
        completionJob?.cancel()
        _state.value = ProgressState.Completed
        completionJob = scope.launch {
            delay(2000)
            if (_state.value == ProgressState.Completed) {
                _state.value = ProgressState.Idle
            }
        }
    }

    fun failTranslation() {
        completionJob?.cancel()
        _state.value = ProgressState.Failed
    }
}
