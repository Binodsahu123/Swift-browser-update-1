package com.swift.browser.tabengine.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin

object TabAnimationTransitions {
    // High-performance spring configurations for fluid physical motion
    val FastSpring: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessHigh,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    val FluidSpring: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioLowBouncy
    )

    val SmoothSpring: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    // Tab Switcher Overlay Entrance (WebView shrinks/fades, Switcher scales/fades in)
    val tabSwitcherEnter: EnterTransition = fadeIn(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    ) + scaleIn(
        initialScale = 0.90f,
        transformOrigin = TransformOrigin.Center,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
    )

    val tabSwitcherExit: ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    ) + scaleOut(
        targetScale = 0.95f,
        transformOrigin = TransformOrigin.Center,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    // Active Tab Switching Content Transform (horizontal slide with subtle scale)
    fun activeTabTransition(isForward: Boolean): ContentTransform {
        val slideOffset = if (isForward) 300 else -300
        val enter = slideInHorizontally(
            initialOffsetX = { slideOffset },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 220)
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )

        val exit = slideOutHorizontally(
            targetOffsetX = { -slideOffset },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 180)
        ) + scaleOut(
            targetScale = 0.96f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )

        return enter togetherWith exit
    }
}

