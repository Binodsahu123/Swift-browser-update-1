package com.swift.browser.searchengine

import java.util.UUID

data class VoiceHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: String = "command",
    val timestamp: Long = System.currentTimeMillis()
)

data class VoiceNote(
    val id: String = UUID.randomUUID().toString(),
    val originalTranscript: String,
    val noteContent: String,
    val format: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class VoiceChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
