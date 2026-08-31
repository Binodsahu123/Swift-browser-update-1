package com.swift.browser.aiengine

import android.content.Context
import com.swift.browser.databasecore.PreferenceManager

class AISettingsManager(context: Context) {
    private val prefs = PreferenceManager(context)

    var defaultProvider: String
        get() = prefs.getString("ai_default_provider", "Gemini")
        set(value) = prefs.setString("ai_default_provider", value)

    var defaultModel: String
        get() = prefs.getString("ai_default_model", "Default")
        set(value) = prefs.setString("ai_default_model", value)

    var preferredLanguage: String
        get() = prefs.getString("ai_preferred_language", "Same as Page")
        set(value) = prefs.setString("ai_preferred_language", value)

    var responseLength: String
        get() = prefs.getString("ai_response_length", "Medium")
        set(value) = prefs.setString("ai_response_length", value)

    var guestModeEnabled: Boolean
        get() = prefs.getBoolean("ai_guest_mode_enabled", true)
        set(value) = prefs.setBoolean("ai_guest_mode_enabled", value)

    var autoLoginEnabled: Boolean
        get() = prefs.getBoolean("ai_auto_login_enabled", true)
        set(value) = prefs.setBoolean("ai_auto_login_enabled", value)

    var responseStyle: String
        get() = prefs.getString("ai_response_style", "Balanced")
        set(value) = prefs.setString("ai_response_style", value)

    var searchGroundingEnabled: Boolean
        get() = prefs.getBoolean("ai_search_grounding_enabled", true)
        set(value) = prefs.setBoolean("ai_search_grounding_enabled", value)

    var chatbotRole: String
        get() = prefs.getString("ai_chatbot_role", "General Assistant")
        set(value) = prefs.setString("ai_chatbot_role", value)

    fun getApiKey(provider: String): String {
        val storedKey = prefs.getString("ai_api_key_${provider.lowercase()}", "")
        if (storedKey.isEmpty() && provider.equals("Gemini", ignoreCase = true)) {
            // Fallback to BuildConfig key if configured
            try {
                return ""
            } catch (e: Exception) {
                // Ignore if BuildConfig is not generated or field missing
            }
        }
        return storedKey
    }

    fun setApiKey(provider: String, key: String) {
        prefs.setString("ai_api_key_${provider.lowercase()}", key)
    }
}
