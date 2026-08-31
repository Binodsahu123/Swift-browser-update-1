package com.swift.browser.securityengine.controller

import com.swift.browser.securityengine.engine.SecurityStateEngine
import com.swift.browser.securityengine.ui.SecurityUiState
import kotlinx.coroutines.flow.StateFlow

class SecurityUiController(
    private val stateEngine: SecurityStateEngine
) {
    val uiState: StateFlow<SecurityUiState> = stateEngine.uiState

    fun updateShieldState(
        httpsUpgrade: Boolean? = null,
        trackerBlocking: Boolean? = null,
        cookieIsolation: Boolean? = null,
        doNotTrack: Boolean? = null,
        safeBrowsing: String? = null
    ) {
        stateEngine.updateShieldState(
            httpsUpgrade = httpsUpgrade,
            trackerBlocking = trackerBlocking,
            cookieIsolation = cookieIsolation,
            doNotTrack = doNotTrack,
            safeBrowsing = safeBrowsing
        )
    }

    fun dismissWarnings() {
        stateEngine.clearWarnings()
    }
}
