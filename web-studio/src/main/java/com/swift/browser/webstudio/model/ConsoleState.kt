package com.swift.browser.webstudio.model

data class ConsoleEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val tag: String = "Console",
    val message: String
)

data class ConsoleState(
    val logs: List<ConsoleEntry> = emptyList(),
    val isVisible: Boolean = false
)
