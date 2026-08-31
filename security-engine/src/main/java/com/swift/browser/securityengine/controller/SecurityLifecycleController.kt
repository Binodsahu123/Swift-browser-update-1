package com.swift.browser.securityengine.controller

import com.swift.browser.securityengine.engine.SecurityDiagnosticsEngine
import com.swift.browser.securityengine.engine.SecurityStateEngine
import com.swift.browser.securityengine.manager.SecurityMemoryManager
import com.swift.browser.securityengine.model.SecuritySession

class SecurityLifecycleController(
    private val stateEngine: SecurityStateEngine,
    private val memoryManager: SecurityMemoryManager,
    private val diagnosticsEngine: SecurityDiagnosticsEngine
) {
    fun startEngine() {
        diagnosticsEngine.logEvent("Security Engine lifecycle started")
    }

    fun stopEngine() {
        diagnosticsEngine.logEvent("Security Engine lifecycle stopped")
    }

    fun saveSession() {
        val session = SecuritySession(
            whitelistedDomains = emptySet(),
            safeBrowsingMode = stateEngine.shieldState.value.safeBrowsingMode
        )
        memoryManager.saveSession(session)
        diagnosticsEngine.logEvent("Security session persisted")
    }

    fun restoreSession(): SecuritySession? {
        val session = memoryManager.restoreSession()
        if (session != null) {
            diagnosticsEngine.logEvent("Security session restored")
        }
        return session
    }
}
