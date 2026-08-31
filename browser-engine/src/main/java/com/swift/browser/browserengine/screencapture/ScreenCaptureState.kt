package com.swift.browser.browserengine.screencapture

/**
 * State machine representation for an active or pending Web Screen-Sharing session.
 * Tracks transitions from initial request to permission evaluation, MediaProjection consent,
 * active display streaming, and graceful termination.
 */
enum class ScreenCaptureState {
    IDLE,
    REQUESTED,
    WAITING_PERMISSION,
    WAITING_MEDIA_PROJECTION,
    CAPTURING,
    STOPPING,
    STOPPED,
    FAILED;

    val isTerminal: Boolean
        get() = this == STOPPED || this == FAILED

    val isActive: Boolean
        get() = this == CAPTURING || this == WAITING_PERMISSION || this == WAITING_MEDIA_PROJECTION || this == REQUESTED
}
