package com.swift.browser.securityengine.manager

import com.swift.browser.securityengine.model.SecuritySession

class SecurityMemoryManager {
    private var currentSession: SecuritySession? = null

    fun saveSession(session: SecuritySession) {
        currentSession = session
    }

    fun restoreSession(): SecuritySession? {
        return currentSession
    }

    fun clearSession() {
        currentSession = null
    }
}
