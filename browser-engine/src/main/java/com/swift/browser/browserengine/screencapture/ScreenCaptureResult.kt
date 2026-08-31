package com.swift.browser.browserengine.screencapture

/**
 * Result model for screen capture initialization requests.
 */
sealed class ScreenCaptureResult {
    data class Success(
        val session: ScreenCaptureSession,
        val width: Int = session.width,
        val height: Int = session.height,
        val frameRate: Int = session.frameRate
    ) : ScreenCaptureResult()

    data class Error(
        val code: String,
        val message: String,
        val diagnostic: String? = null
    ) : ScreenCaptureResult()
}
