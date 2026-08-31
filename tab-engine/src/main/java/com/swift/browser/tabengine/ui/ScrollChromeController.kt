package com.swift.browser.tabengine.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ScrollChromeController(private val scope: CoroutineScope) {
    // Current offset of the toolbars. 0 = fully visible.
    // 1f = fully hidden.
    private val _hideProgress = MutableStateFlow(0f)
    val hideProgress: StateFlow<Float> = _hideProgress.asStateFlow()
    
    private val animatable = Animatable(0f)

    private var accumulatedScroll = 0f
    private val maxScrollDistance = 250f

    fun onScroll(dy: Int, isTop: Boolean) {
        if (isTop) {
            accumulatedScroll = 0f
            _hideProgress.value = 0f
            scope.launch { animatable.snapTo(0f) }
            return
        }
        
        // Apply a small friction to scrolling to make it feel smoother
        accumulatedScroll += dy * 0.8f
        accumulatedScroll = accumulatedScroll.coerceIn(0f, maxScrollDistance)
        
        val progress = accumulatedScroll / maxScrollDistance
        _hideProgress.value = progress
        scope.launch { animatable.snapTo(progress) }
    }
    
    fun animateToRestState(show: Boolean) {
        val targetProgress = if (show) 0f else 1f
        accumulatedScroll = if (show) 0f else maxScrollDistance
        
        scope.launch {
            animatable.animateTo(
                targetValue = targetProgress,
                animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
            ) {
                _hideProgress.value = value
            }
        }
    }

    fun reset() {
        accumulatedScroll = 0f
        _hideProgress.value = 0f
        scope.launch { animatable.snapTo(0f) }
    }
    
    fun setVisible(visible: Boolean) {
        if (visible) {
            accumulatedScroll = 0f
            _hideProgress.value = 0f
            scope.launch { animatable.snapTo(0f) }
        } else {
            accumulatedScroll = maxScrollDistance
            _hideProgress.value = 1f
            scope.launch { animatable.snapTo(1f) }
        }
    }
}
