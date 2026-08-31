package com.swift.browser.videoengine.live

sealed class LiveStreamEvent {
    data class StateChanged(val state: LiveStreamState, val message: String? = null) : LiveStreamEvent()
    data class ErrorOccurred(val errorCode: String, val message: String, val exception: Throwable? = null) : LiveStreamEvent()
    data class WarningOccurred(val warningCode: String, val message: String) : LiveStreamEvent()
    data class StatsUpdated(val stats: LiveStreamStats) : LiveStreamEvent()
}
