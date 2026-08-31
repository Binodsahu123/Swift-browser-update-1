package com.swift.browser.analyticscore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionAnalyticsManager {
    private val _currentSession = MutableStateFlow(SessionState())
    val currentSession: StateFlow<SessionState> = _currentSession.asStateFlow()

    private val _sessionHistory = MutableStateFlow<List<SessionState>>(emptyList())
    val sessionHistory: StateFlow<List<SessionState>> = _sessionHistory.asStateFlow()

    fun startNewSession() {
        // End current session if active
        val oldSession = _currentSession.value
        if (oldSession.endTimeMs == null) {
            oldSession.endTimeMs = System.currentTimeMillis()
            archiveSession(oldSession)
        }

        _currentSession.value = SessionState()
    }

    fun incrementPageViews() {
        val session = _currentSession.value
        session.pageViewsCount += 1
        _currentSession.value = session.copy(pageViewsCount = session.pageViewsCount)
    }

    fun updateActiveTabCount(count: Int) {
        val session = _currentSession.value
        session.activeTabCount = count
        _currentSession.value = session.copy(activeTabCount = count)
    }

    fun setBackgroundState(isBackgrounded: Boolean) {
        val session = _currentSession.value
        session.isBackgrounded = isBackgrounded
        if (isBackgrounded) {
            session.endTimeMs = System.currentTimeMillis()
        }
        _currentSession.value = session.copy(isBackgrounded = isBackgrounded)
    }

    private fun archiveSession(session: SessionState) {
        val current = _sessionHistory.value.toMutableList()
        if (current.size >= 50) {
            current.removeAt(0)
        }
        current.add(session)
        _sessionHistory.value = current
    }

    fun endSession() {
        val session = _currentSession.value
        session.endTimeMs = System.currentTimeMillis()
        archiveSession(session)
    }
}
