package com.swift.browser.audioengine.tts

data class TtsState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val currentText: String = "",
    val currentIndex: Int = 0,
    val totalSegments: Int = 0,
    val speed: Float = 1.0f
)
