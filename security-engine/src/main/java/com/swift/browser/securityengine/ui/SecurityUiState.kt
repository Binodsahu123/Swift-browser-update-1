package com.swift.browser.securityengine.ui

import com.swift.browser.securityengine.SslWarningState
import com.swift.browser.securityengine.SecurityShieldState
import com.swift.browser.securityengine.model.SecurityState
import com.swift.browser.securityengine.model.SecurityWarning
import com.swift.browser.securityengine.model.SecurityThreat

data class SecurityUiState(
    val securityState: SecurityState = SecurityState(),
    val shieldState: SecurityShieldState = SecurityShieldState(),
    val sslWarningState: SslWarningState = SslWarningState(),
    val currentWarning: SecurityWarning? = null,
    val currentThreat: SecurityThreat? = null,
    val isProtectionActive: Boolean = true,
    val diagnosticsLog: String = "Security Engine operational"
)
