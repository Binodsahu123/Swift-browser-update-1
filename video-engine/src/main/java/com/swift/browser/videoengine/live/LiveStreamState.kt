package com.swift.browser.videoengine.live

enum class LiveStreamState {
    IDLE,
    PREPARING,
    INITIALIZING_VIDEO,
    INITIALIZING_AUDIO,
    ENCODING,
    CONNECTING,
    STREAMING,
    RECONNECTING,
    STOPPING,
    STOPPED,
    FAILED
}
