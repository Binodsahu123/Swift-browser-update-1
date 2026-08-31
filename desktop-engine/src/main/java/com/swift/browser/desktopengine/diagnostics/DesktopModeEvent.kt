package com.swift.browser.desktopengine.diagnostics

sealed class DesktopModeEvent {
    data class ModeToggled(val host: String, val isDesktop: Boolean, val timestamp: Long = System.currentTimeMillis()) : DesktopModeEvent()
    data class UserAgentApplied(val host: String, val userAgent: String) : DesktopModeEvent()
    data class ViewportApplied(val host: String, val width: Int, val scale: Float) : DesktopModeEvent()
    data class UrlRewritten(val originalUrl: String, val rewrittenUrl: String) : DesktopModeEvent()
    data class MediaStateCaptured(val host: String, val playbackPosition: Float, val isPlaying: Boolean) : DesktopModeEvent()
    data class MediaStateRestored(val host: String, val playbackPosition: Float, val success: Boolean) : DesktopModeEvent()
    data class ExecutionFailed(val engine: String, val className: String, val methodName: String, val reason: String) : DesktopModeEvent()
}
