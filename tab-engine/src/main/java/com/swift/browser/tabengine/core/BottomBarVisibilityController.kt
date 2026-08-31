package com.swift.browser.tabengine.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BottomBarVisibilityController(private val scope: CoroutineScope) {
    private val _hideProgress = MutableStateFlow(0f)
    val hideProgress: StateFlow<Float> = _hideProgress.asStateFlow()
    
    private val animatable = Animatable(0f)

    fun updateProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        _hideProgress.value = clamped
        scope.launch { animatable.snapTo(clamped) }
    }
    
    fun animateTo(target: Float) {
        val clampedTarget = target.coerceIn(0f, 1f)
        scope.launch {
            animatable.animateTo(
                targetValue = clampedTarget,
                animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
            ) {
                _hideProgress.value = value
            }
        }
    }
}
