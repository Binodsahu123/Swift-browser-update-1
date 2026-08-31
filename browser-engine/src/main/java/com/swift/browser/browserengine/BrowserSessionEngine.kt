package com.swift.browser.browserengine

import android.util.Log

class BrowserSessionEngine(private val stateEngine: BrowserStateEngine) {
    companion object {
        private const val TAG = "BrowserSessionEngine"
    }
    private var currentSession = BrowserSession()

    fun saveSession(activeTabId: String = "", tabUrls: List<String> = emptyList()): BrowserSession {
        currentSession = BrowserSession(
            activeTabId = activeTabId,
            tabUrls = tabUrls
        )
        Log.d(TAG, "Saved session with ${tabUrls.size} tabs")
        stateEngine.updateDiagnostics("Session saved: ${tabUrls.size} tabs")
        return currentSession
    }

    fun restoreSession(session: BrowserSession) {
        currentSession = session
        stateEngine.updateState(BrowserState.RESTORING)
        Log.d(TAG, "Restoring session ID: ${session.sessionId}")
        stateEngine.updateDiagnostics("Session restored: ${session.tabUrls.size} tabs")
        stateEngine.updateState(BrowserState.READY)
    }

    fun getCurrentSession(): BrowserSession = currentSession
}
