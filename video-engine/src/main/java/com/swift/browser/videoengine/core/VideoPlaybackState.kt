package com.swift.browser.videoengine.core

enum class PlaybackStatus {
    IDLE,
    PREPARING,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    RELEASED,
    ERROR
}

data class VideoPlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)
