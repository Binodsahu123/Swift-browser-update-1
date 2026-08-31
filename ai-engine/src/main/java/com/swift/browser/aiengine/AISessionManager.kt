package com.swift.browser.aiengine

import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class AISession(
    val tabId: String,
    val websiteUrl: String,
    val host: String,
    val rawPageText: String,
    val parsedSummary: AIAnalysisResult?,
    val chatHistory: List<Pair<String, String>>
)

object AISessionManager {
    private const val TAG = "AISessionManager"
    
    // Maps active Android Browser tabId strings to corresponding AI session states
    private val sessionsMap = ConcurrentHashMap<String, AISession>()

    fun getSession(tabId: String): AISession? {
        return sessionsMap[tabId]
    }

    /**
     * Instantiates or updates session data for a specific tab.
     * If the tab navigates to a new page on the *same domain*, we maintain the active conversation.
     * If the domain changes, we initiate a clean slate.
     */
    fun updateSessionPage(
        tabId: String,
        url: String,
        pageText: String,
        summary: AIAnalysisResult?
    ) {
        val newHost = try {
            Uri.parse(url).host ?: ""
        } catch (e: Exception) {
            ""
        }

        val existingSession = sessionsMap[tabId]
        val preservedChatHistory = if (existingSession != null && 
            existingSession.host.isNotEmpty() && 
            existingSession.host.equals(newHost, ignoreCase = true)
        ) {
            Log.d(TAG, "Navigated within same host '$newHost'. Preserving active AI chat conversation.")
            existingSession.chatHistory
        } else {
            Log.d(TAG, "New host '$newHost' or first-run detected. Resetting chat conversation history.")
            emptyList()
        }

        val updatedSession = AISession(
            tabId = tabId,
            websiteUrl = url,
            host = newHost,
            rawPageText = pageText,
            parsedSummary = summary,
            chatHistory = preservedChatHistory
        )
        sessionsMap[tabId] = updatedSession
    }

    /**
     * Updates only the chat conversation history part of the active tab.
     */
    fun updateChatHistory(tabId: String, history: List<Pair<String, String>>) {
        val current = sessionsMap[tabId] ?: return
        sessionsMap[tabId] = current.copy(chatHistory = history)
    }

    /**
     * Manually deletes or resets the AI memory cache for a tab when it is closed.
     */
    fun clearSession(tabId: String) {
        sessionsMap.remove(tabId)
        Log.i(TAG, "AI Session cleared for closed tab '$tabId'")
    }

    fun clearAll() {
        sessionsMap.clear()
        Log.i(TAG, "All active AI sessions wiped from memory cache")
    }
}
