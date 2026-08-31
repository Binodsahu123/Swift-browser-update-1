package com.swift.browser.readerengine.model

data class ReaderState(
    val isAvailable: Boolean = false,
    val availableTabs: Set<String> = emptySet(),
    val isActive: Boolean = false,
    val title: String = "",
    val author: String? = null,
    val date: String? = null,
    val domain: String? = null,
    val content: String = "",
    val fontSize: Int = 16,
    val isSerif: Boolean = false,
    val theme: String = "Dark"
)
