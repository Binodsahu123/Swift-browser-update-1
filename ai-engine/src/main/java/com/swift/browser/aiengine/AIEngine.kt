package com.swift.browser.aiengine

import android.content.Context

interface AIEngine {
    suspend fun generateResponse(prompt: String): String
}

class PromptManager {
    fun createSystemPrompt(userTask: String): String {
        return "You are an assistant. Task: $userTask"
    }
}

class ChatManager(private val aiEngine: AIEngine) {
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    suspend fun sendMessage(message: String): String {
        val response = aiEngine.generateResponse(message)
        conversationHistory.add(message to response)
        return response
    }

    fun getHistory(): List<Pair<String, String>> = conversationHistory.toList()
}

class SummaryEngine(private val aiEngine: AIEngine) {
    suspend fun summarizeWebpage(htmlContent: String): String {
        if (htmlContent.isBlank()) return "No content to summarize."
        return aiEngine.generateResponse("Summarize the following text briefly: $htmlContent")
    }
}
