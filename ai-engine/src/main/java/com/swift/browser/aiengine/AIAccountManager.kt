package com.swift.browser.aiengine

import android.content.Context
import com.swift.browser.databasecore.PreferenceManager

data class AIAccount(
    val provider: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val sessionInfo: String,
    val isLoggedIn: Boolean
)

class AIAccountManager(context: Context) {
    private val prefs = PreferenceManager(context)

    fun getAccount(provider: String): AIAccount? {
        val keyPrefix = "ai_acc_${provider.lowercase()}"
        val isLoggedIn = prefs.getBoolean("${keyPrefix}_is_logged", false)
        if (!isLoggedIn) return null

        return AIAccount(
            provider = provider,
            email = prefs.getString("${keyPrefix}_email", ""),
            accessToken = prefs.getString("${keyPrefix}_access_token", ""),
            refreshToken = prefs.getString("${keyPrefix}_refresh_token", ""),
            sessionInfo = prefs.getString("${keyPrefix}_session_info", ""),
            isLoggedIn = true
        )
    }

    fun saveAccount(account: AIAccount) {
        val keyPrefix = "ai_acc_${account.provider.lowercase()}"
        prefs.setBoolean("${keyPrefix}_is_logged", true)
        prefs.setString("${keyPrefix}_email", account.email)
        prefs.setString("${keyPrefix}_access_token", account.accessToken)
        prefs.setString("${keyPrefix}_refresh_token", account.refreshToken)
        prefs.setString("${keyPrefix}_session_info", account.sessionInfo)
    }

    fun deleteAccount(provider: String) {
        val keyPrefix = "ai_acc_${provider.lowercase()}"
        prefs.setBoolean("${keyPrefix}_is_logged", false)
        prefs.setString("${keyPrefix}_email", "")
        prefs.setString("${keyPrefix}_access_token", "")
        prefs.setString("${keyPrefix}_refresh_token", "")
        prefs.setString("${keyPrefix}_session_info", "")
    }

    fun isLoggedIn(provider: String): Boolean {
        return prefs.getBoolean("ai_acc_${provider.lowercase()}_is_logged", false)
    }
}
