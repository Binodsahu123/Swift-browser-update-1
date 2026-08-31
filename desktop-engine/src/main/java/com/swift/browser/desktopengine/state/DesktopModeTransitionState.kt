package com.swift.browser.desktopengine.state

sealed class DesktopModeTransitionState {
    object Idle : DesktopModeTransitionState()
    object Preparing : DesktopModeTransitionState()
    object CapturingPageState : DesktopModeTransitionState()
    object ApplyingWebSettings : DesktopModeTransitionState()
    object ApplyingUserAgent : DesktopModeTransitionState()
    object ApplyingViewport : DesktopModeTransitionState()
    object ApplyingMetrics : DesktopModeTransitionState()
    object ApplyingCompatibility : DesktopModeTransitionState()
    object Navigating : DesktopModeTransitionState()
    object RestoringPageState : DesktopModeTransitionState()
    object Completed : DesktopModeTransitionState()
    data class Failed(val error: String) : DesktopModeTransitionState()
}
