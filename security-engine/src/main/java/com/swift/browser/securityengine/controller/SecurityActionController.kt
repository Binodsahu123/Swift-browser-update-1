package com.swift.browser.securityengine.controller

import com.swift.browser.securityengine.engine.*
import com.swift.browser.securityengine.util.SecurityUtils

class SecurityActionController(
    private val safeBrowsingEngine: SafeBrowsingEngine,
    private val threatDetectionEngine: ThreatDetectionEngine,
    private val sslPolicyEngine: SslPolicyEngine,
    private val malwareScanEngine: MalwareScanEngine,
    private val stateEngine: SecurityStateEngine,
    private val diagnosticsEngine: SecurityDiagnosticsEngine
) {
    fun checkUrlSafety(url: String, isPrivate: Boolean = false): Boolean {
        val mode = stateEngine.shieldState.value.safeBrowsingMode
        val isSafe = safeBrowsingEngine.isUrlSafe(url, mode, isPrivate)
        if (!isSafe) {
            val threat = threatDetectionEngine.detectThreat(url, isPrivate)
            stateEngine.setThreat(threat, isPrivate)
            diagnosticsEngine.logEvent("Blocked unsafe URL: $url", isPrivate)
        }
        return isSafe
    }

    fun proceedSslWarning(url: String, isPrivate: Boolean = false) {
        val host = SecurityUtils.extractHost(url)
        if (host.isNotBlank()) {
            sslPolicyEngine.whitelistDomain(host)
            diagnosticsEngine.logEvent("Whitelisted SSL domain: $host", isPrivate)
        }
        stateEngine.clearWarnings()
    }

    fun blockSslWarning(url: String, isPrivate: Boolean = false) {
        diagnosticsEngine.logEvent("User blocked SSL warning for URL: $url", isPrivate)
        stateEngine.clearWarnings()
    }

    fun analyzeDownloadSafety(url: String, contentDisposition: String?, mimeType: String?, isPrivate: Boolean = false): Boolean {
        val mode = stateEngine.shieldState.value.safeBrowsingMode
        val isSafe = malwareScanEngine.isDownloadSafe(url, contentDisposition, mimeType, mode)
        if (!isSafe) {
            diagnosticsEngine.logEvent("Blocked malicious download from: $url", isPrivate)
        }
        return isSafe
    }
}
