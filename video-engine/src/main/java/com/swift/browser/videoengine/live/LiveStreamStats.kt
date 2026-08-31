package com.swift.browser.videoengine.live

data class LiveStreamStats(
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val totalBytesSent: Long = 0L,
    val droppedFrames: Int = 0,
    val rttMs: Long = 0L,
    val rtmpState: String = "DISCONNECTED"
)
