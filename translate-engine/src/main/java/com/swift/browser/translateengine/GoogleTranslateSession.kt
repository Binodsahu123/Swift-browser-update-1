package com.swift.browser.translateengine

import java.util.UUID

class GoogleTranslateSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val sourceLang: String,
    val targetLang: String,
    val startTime: Long = System.currentTimeMillis()
) {
    private val sessionTranslations = mutableMapOf<String, String>()

    fun recordTranslation(original: String, translated: String) {
        synchronized(sessionTranslations) {
            sessionTranslations[original] = translated
        }
    }

    fun getSessionTranslations(): Map<String, String> {
        synchronized(sessionTranslations) {
            return sessionTranslations.toMap()
        }
    }

    fun clearSession() {
        synchronized(sessionTranslations) {
            sessionTranslations.clear()
        }
    }
}
