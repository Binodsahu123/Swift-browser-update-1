package com.swift.browser.securityengine.engine

import com.swift.browser.securityengine.SecurityShieldState
import com.swift.browser.securityengine.SslWarningState
import com.swift.browser.securityengine.model.*
import com.swift.browser.securityengine.ui.SecurityUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.swift.browser.securityengine.util.SecurityUtils

class SecurityStateEngine {
    private val _securityState = MutableStateFlow(SecurityState())
    val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()

    private val _shieldState = MutableStateFlow(SecurityShieldState())
    val shieldState: StateFlow<SecurityShieldState> = _shieldState.asStateFlow()

    private val _sslWarningState = MutableStateFlow(SslWarningState())
    val sslWarningState: StateFlow<SslWarningState> = _sslWarningState.asStateFlow()

    private val _currentWarning = MutableStateFlow<SecurityWarning?>(null)
    val currentWarning: StateFlow<SecurityWarning?> = _currentWarning.asStateFlow()

    private val _currentThreat = MutableStateFlow<SecurityThreat?>(null)
    val currentThreat: StateFlow<SecurityThreat?> = _currentThreat.asStateFlow()

    private val _currentError = MutableStateFlow<SecurityError?>(null)
    val currentError: StateFlow<SecurityError?> = _currentError.asStateFlow()

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    fun updateShieldState(
        httpsUpgrade: Boolean? = null,
        trackerBlocking: Boolean? = null,
        cookieIsolation: Boolean? = null,
        doNotTrack: Boolean? = null,
        safeBrowsing: String? = null
    ) {
        val curr = _shieldState.value
        val updated = curr.copy(
            httpsUpgradeEnabled = httpsUpgrade ?: curr.httpsUpgradeEnabled,
            trackerBlockingEnabled = trackerBlocking ?: curr.trackerBlockingEnabled,
            cookieIsolationEnabled = cookieIsolation ?: curr.cookieIsolationEnabled,
            doNotTrackEnabled = doNotTrack ?: curr.doNotTrackEnabled,
            safeBrowsingMode = safeBrowsing ?: curr.safeBrowsingMode
        )
        _shieldState.value = updated
        syncUiState()
    }

    fun setSecurityStatus(status: SecurityStatus, currentUrl: String = "", riskScore: Int = 0, isPrivate: Boolean = false) {
        val sanitizedUrl = if (isPrivate && currentUrl.isNotEmpty()) {
            val host = SecurityUtils.extractHost(currentUrl)
            if (host.isNotBlank()) "https://$host/[PRIVATE_PAGE]" else "[PRIVATE_PAGE]"
        } else {
            currentUrl
        }
        _securityState.value = _securityState.value.copy(
            status = status,
            currentUrl = sanitizedUrl,
            riskScore = riskScore,
            lastCheckedTimestamp = System.currentTimeMillis()
        )
        syncUiState()
    }

    fun setSslWarning(state: SslWarningState, isPrivate: Boolean = false) {
        _sslWarningState.value = state
        if (state.showWarning) {
            setSecurityStatus(SecurityStatus.SSL_WARNING, state.url, 75, isPrivate)
        }
        syncUiState()
    }

    fun setWarning(warning: SecurityWarning?, isPrivate: Boolean = false) {
        if (warning != null && isPrivate) {
            val host = SecurityUtils.extractHost(warning.url)
            val sanitized = warning.copy(url = if (host.isNotBlank()) "https://$host/[PRIVATE]" else "[PRIVATE]")
            _currentWarning.value = sanitized
            setSecurityStatus(SecurityStatus.WARNING, sanitized.url, 80, isPrivate = true)
        } else {
            _currentWarning.value = warning
            if (warning != null) {
                setSecurityStatus(SecurityStatus.WARNING, warning.url, 80, isPrivate = false)
            }
        }
        syncUiState()
    }

    fun setThreat(threat: SecurityThreat?, isPrivate: Boolean = false) {
        if (threat != null && isPrivate) {
            val host = SecurityUtils.extractHost(threat.targetUrl)
            val sanitized = threat.copy(targetUrl = if (host.isNotBlank()) "https://$host/[PRIVATE]" else "[PRIVATE]")
            _currentThreat.value = sanitized
            setSecurityStatus(SecurityStatus.THREAT_DETECTED, sanitized.targetUrl, 100, isPrivate = true)
        } else {
            _currentThreat.value = threat
            if (threat != null) {
                setSecurityStatus(SecurityStatus.THREAT_DETECTED, threat.targetUrl, 100, isPrivate = false)
            }
        }
        syncUiState()
    }

    fun setError(error: SecurityError?, isPrivate: Boolean = false) {
        if (error != null && isPrivate) {
            val host = SecurityUtils.extractHost(error.failingUrl)
            val sanitized = error.copy(failingUrl = if (host.isNotBlank()) "https://$host/[PRIVATE]" else "[PRIVATE]")
            _currentError.value = sanitized
            setSecurityStatus(SecurityStatus.ERROR, sanitized.failingUrl, 50, isPrivate = true)
        } else {
            _currentError.value = error
            if (error != null) {
                setSecurityStatus(SecurityStatus.ERROR, error.failingUrl, 50, isPrivate = false)
            }
        }
        syncUiState()
    }

    fun clearWarnings() {
        _sslWarningState.value = SslWarningState()
        _currentWarning.value = null
        _currentThreat.value = null
        _currentError.value = null
        _securityState.value = _securityState.value.copy(status = SecurityStatus.SAFE, riskScore = 0)
        syncUiState()
    }

    private fun syncUiState() {
        _uiState.value = SecurityUiState(
            securityState = _securityState.value,
            shieldState = _shieldState.value,
            sslWarningState = _sslWarningState.value,
            currentWarning = _currentWarning.value,
            currentThreat = _currentThreat.value,
            isProtectionActive = _shieldState.value.safeBrowsingMode != "No"
        )
    }
}
