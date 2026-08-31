package com.swift.browser.webstudio.model

data class RuntimeState(
    val isRunning: Boolean = false,
    val statusMessage: String = "Idle",
    val activeProcessId: String? = null
)
