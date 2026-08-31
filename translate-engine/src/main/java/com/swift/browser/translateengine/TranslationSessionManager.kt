package com.swift.browser.translateengine

import java.util.concurrent.ConcurrentHashMap

data class TranslationSession(
    val tabId: String,
    var domain: String,
    var isTranslationActive: Boolean,
    var targetLanguageCode: String,
    var targetLanguageName: String
)

object TranslationSessionManager {
    private val sessions = ConcurrentHashMap<String, TranslationSession>()
    private val activeDomains = ConcurrentHashMap<String, String>() // domain -> targetLangCode

    fun getSession(tabId: String): TranslationSession? {
        return sessions[tabId]
    }

    fun startSession(tabId: String, domain: String, targetLangCode: String, targetLangName: String) {
        val session = sessions.getOrPut(tabId) {
            TranslationSession(tabId, domain, true, targetLangCode, targetLangName)
        }
        session.domain = domain
        session.isTranslationActive = true
        session.targetLanguageCode = targetLangCode
        session.targetLanguageName = targetLangName
        
        if (domain.isNotEmpty()) {
            activeDomains[domain] = targetLangCode
        }
    }

    fun updateSessionDomain(tabId: String, newDomain: String) {
        sessions[tabId]?.let {
            it.domain = newDomain
        }
    }

    fun stopSession(tabId: String) {
        sessions[tabId]?.let {
            it.isTranslationActive = false
        }
    }

    fun disableDomainTranslation(domain: String) {
        if (domain.isNotEmpty()) {
            activeDomains.remove(domain)
            sessions.values.forEach { session ->
                if (session.domain == domain) {
                    session.isTranslationActive = false
                }
            }
        }
    }

    fun isDomainTranslationActive(domain: String): Boolean {
        return domain.isNotEmpty() && activeDomains.containsKey(domain)
    }

    fun getDomainTargetLanguageCode(domain: String): String? {
        return activeDomains[domain]
    }

    fun clearAll() {
        sessions.clear()
        activeDomains.clear()
    }
}
