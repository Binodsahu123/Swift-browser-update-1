package com.swift.browser.aiengine

import android.content.Context
import android.util.Log

object AIProviderRouter {
    private const val TAG = "AIProviderRouter"

    // Primary chain of fallback options
    private val fallbackChain = listOf(
        "ChatGPT",
        "Gemini",
        "Claude",
        "DeepSeek",
        "Qwen",
        "Mistral",
        "Grok",
        "Custom Provider"
    )

    /**
     * Given a failed provider, resolves which alternative provider to attempt next.
     */
    fun findFallbackProvider(failedProvider: String, context: Context): String? {
        Log.w(TAG, "Provider '$failedProvider' is unavailable or failed. Computing fallback...")
        
        val settingsManager = AISettingsManager(context)
        val guestModeManager = AIGuestModeManager(context)
        val accountManager = AIAccountManager(context)

        // Find index of the failed provider in our baseline chain
        val currentIndex = fallbackChain.indexOfFirst { it.equals(failedProvider, ignoreCase = true) }
        
        // Scan onward starting from the next provider in the chain
        val startIndex = if (currentIndex != -1) currentIndex + 1 else 0

        for (i in startIndex until fallbackChain.size) {
            val candidate = fallbackChain[i]
            
            // Validate if the candidate is logged in OR allows Guest configuration
            val hasAccess = accountManager.isLoggedIn(candidate) || 
                            (candidate.equals("Gemini", ignoreCase = true)) || // always fallback to Gemini fallback key
                            guestModeManager.isGuestAllowed(candidate)

            if (hasAccess) {
                Log.i(TAG, "Failover match detected! Switching from '$failedProvider' to '$candidate'")
                return candidate
            }
        }

        // Ultimate safety default
        return if (!failedProvider.equals("Gemini", ignoreCase = true)) "Gemini" else null
    }
}
