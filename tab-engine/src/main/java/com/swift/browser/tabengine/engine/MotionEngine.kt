package com.swift.browser.tabengine.engine

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MotionEngine(private val scope: CoroutineScope) {
    private val _toolbarAlpha = MutableStateFlow(1f)
    val toolbarAlpha: StateFlow<Float> = _toolbarAlpha.asStateFlow()

    private val _toolbarTranslationY = MutableStateFlow(0f)
    val toolbarTranslationY: StateFlow<Float> = _toolbarTranslationY.asStateFlow()

    private val _bottomBarTranslationY = MutableStateFlow(0f)
    val bottomBarTranslationY: StateFlow<Float> = _bottomBarTranslationY.asStateFlow()

    private val _bottomBarAlpha = MutableStateFlow(1f)
    val bottomBarAlpha: StateFlow<Float> = _bottomBarAlpha.asStateFlow()

    private val toolbarAnimatable = Animatable(0f)
    private val bottomBarAnimatable = Animatable(0f)

    fun updateScrollProgress(progress: Float, maxToolbarOffset: Float, maxBottomBarOffset: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        _toolbarTranslationY.value = -clampedProgress * maxToolbarOffset
        _toolbarAlpha.value = 1f - (clampedProgress * 0.5f)
        
        _bottomBarTranslationY.value = clampedProgress * maxBottomBarOffset
        _bottomBarAlpha.value = 1f - (clampedProgress * 0.3f)
    }

    fun animateToRestState(show: Boolean, maxToolbarOffset: Float, maxBottomBarOffset: Float) {
        val targetProgress = if (show) 0f else 1f
        scope.launch {
            toolbarAnimatable.snapTo(_toolbarTranslationY.value / -maxToolbarOffset)
            toolbarAnimatable.animateTo(
                targetValue = targetProgress,
                animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
            ) {
                updateScrollProgress(value, maxToolbarOffset, maxBottomBarOffset)
            }
        }
    }
}
