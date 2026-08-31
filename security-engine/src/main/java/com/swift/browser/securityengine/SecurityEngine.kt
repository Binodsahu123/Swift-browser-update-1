package com.swift.browser.securityengine

import android.util.Log
import com.swift.browser.securityengine.api.SecurityEngineApi
import com.swift.browser.securityengine.controller.SecurityActionController
import com.swift.browser.securityengine.controller.SecurityLifecycleController
import com.swift.browser.securityengine.controller.SecurityUiController
import com.swift.browser.securityengine.engine.*
import com.swift.browser.securityengine.manager.SecurityCacheManager
import com.swift.browser.securityengine.manager.SecurityMemoryManager
import com.swift.browser.securityengine.manager.SecurityRepositoryManager
import com.swift.browser.securityengine.manager.SecurityRiskManager
import com.swift.browser.securityengine.model.*
import com.swift.browser.securityengine.repository.SecurityRepository
import com.swift.browser.securityengine.ui.SecurityUiState
import com.swift.browser.securityengine.util.SecurityUtils
import kotlinx.coroutines.flow.StateFlow

interface SecurityEngine {
    fun isUrlSafe(url: String): Boolean
    fun isUrlSafe(url: String, isPrivate: Boolean): Boolean = isUrlSafe(url)
    fun checkCertificate(url: String): CertificateCheckResult
    fun checkCertificate(url: String, isPrivate: Boolean): CertificateCheckResult = checkCertificate(url)
    fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?): Boolean
    fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?, isPrivate: Boolean): Boolean = isDownloadSafe(url, contentDisposition, mimeType)
}

data class CertificateCheckResult(
    val isValid: Boolean,
    val subject: String = "",
    val issuer: String = "",
    val error: String = ""
)

data class SslWarningState(
    val showWarning: Boolean = false,
    val url: String = "",
    val errorString: String = "",
    val handler: android.webkit.SslErrorHandler? = null,
    val isShowing: Boolean = showWarning,
    val failingUrl: String = url
)

data class SecurityShieldState(
    val httpsUpgradeEnabled: Boolean = true,
    val trackerBlockingEnabled: Boolean = true,
    val cookieIsolationEnabled: Boolean = true,
    val doNotTrackEnabled: Boolean = false,
    val safeBrowsingMode: String = "Enhanced"
)

object SwiftSecurityEngine : SecurityEngine, SecurityEngineApi {
    private const val TAG = "SwiftSecurityEngine"

    // Engine Core Assembly
    private val securityRepo = SecurityRepository()
    private val repoManager = SecurityRepositoryManager(securityRepo)
    private val cacheManager = SecurityCacheManager()
    private val memoryManager = SecurityMemoryManager()
    private val riskManager = SecurityRiskManager()

    private val safeBrowsingEngine = SafeBrowsingEngine(repoManager, cacheManager)
    private val threatDetectionEngine = ThreatDetectionEngine(repoManager)
    private val sslPolicyEngine = SslPolicyEngine(repoManager)
    private val malwareScanEngine = MalwareScanEngine()
    private val stateEngine = SecurityStateEngine()
    private val diagnosticsEngine = SecurityDiagnosticsEngine()

    private val actionController = SecurityActionController(
        safeBrowsingEngine, threatDetectionEngine, sslPolicyEngine, malwareScanEngine, stateEngine, diagnosticsEngine
    )
    private val lifecycleController = SecurityLifecycleController(
        stateEngine, memoryManager, diagnosticsEngine
    )
    private val uiController = SecurityUiController(stateEngine)

    // Backwards Compatible StateFlows
    val shieldState: StateFlow<SecurityShieldState> = stateEngine.shieldState
    val sslWarningState: StateFlow<SslWarningState> = stateEngine.sslWarningState
    val uiState: StateFlow<SecurityUiState> = uiController.uiState

    init {
        lifecycleController.startEngine()
    }

    // Engine Shield State Mutator
    fun updateShieldState(
        httpsUpgrade: Boolean? = null,
        trackerBlocking: Boolean? = null,
        cookieIsolation: Boolean? = null,
        doNotTrack: Boolean? = null,
        safeBrowsing: String? = null
    ) {
        uiController.updateShieldState(
            httpsUpgrade = httpsUpgrade,
            trackerBlocking = trackerBlocking,
            cookieIsolation = cookieIsolation,
            doNotTrack = doNotTrack,
            safeBrowsing = safeBrowsing
        )
    }

    fun setSslWarningState(warningState: SslWarningState) {
        stateEngine.setSslWarning(warningState)
    }

    fun dismissSslWarning() {
        stateEngine.setSslWarning(SslWarningState(showWarning = false))
    }

    fun whitelistSslDomain(host: String) {
        whitelistDomain(host)
    }

    fun isSslWhitelisted(host: String): Boolean {
        return sslPolicyEngine.isSslWhitelisted(host)
    }

    // SecurityEngine interface implementation
    override fun isUrlSafe(url: String): Boolean {
        return checkUrlSafety(url, isPrivate = false)
    }

    override fun isUrlSafe(url: String, isPrivate: Boolean): Boolean {
        return checkUrlSafety(url, isPrivate = isPrivate)
    }

    override fun checkCertificate(url: String): CertificateCheckResult {
        return sslPolicyEngine.checkCertificate(url)
    }

    override fun checkCertificate(url: String, isPrivate: Boolean): CertificateCheckResult {
        return sslPolicyEngine.checkCertificate(url)
    }

    override fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?): Boolean {
        return analyzeDownloadSafety(url, contentDisposition, mimeType, isPrivate = false)
    }

    override fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?, isPrivate: Boolean): Boolean {
        return analyzeDownloadSafety(url, contentDisposition, mimeType, isPrivate = isPrivate)
    }

    // SecurityEngineApi interface implementation
    override fun checkUrlSafety(url: String): Boolean {
        return actionController.checkUrlSafety(url, isPrivate = false)
    }

    override fun checkUrlSafety(url: String, isPrivate: Boolean): Boolean {
        return actionController.checkUrlSafety(url, isPrivate = isPrivate)
    }

    override fun checkSecurityState(url: String): SecurityState {
        return checkSecurityState(url, isPrivate = false)
    }

    override fun checkSecurityState(url: String, isPrivate: Boolean): SecurityState {
        val isSafe = isUrlSafe(url, isPrivate = isPrivate)
        val host = SecurityUtils.extractHost(url)
        val isWhitelisted = sslPolicyEngine.isSslWhitelisted(host)
        val riskScore = riskManager.calculateRiskScore(url, url.startsWith("https://"), !isSafe)

        val status = when {
            !isSafe -> SecurityStatus.BLOCKED
            !url.startsWith("https://") && !isWhitelisted && !SecurityUtils.isLocalOrInternalUrl(url) -> SecurityStatus.WARNING
            else -> SecurityStatus.SAFE
        }

        val displayUrl = if (isPrivate) (if (host.isNotBlank()) "https://$host/[PRIVATE_PAGE]" else "[PRIVATE_PAGE]") else url

        val state = SecurityState(
            status = status,
            currentUrl = displayUrl,
            riskScore = riskScore,
            isProtected = shieldState.value.safeBrowsingMode != "No",
            lastCheckedTimestamp = System.currentTimeMillis()
        )
        stateEngine.setSecurityStatus(status, url, riskScore, isPrivate = isPrivate)
        return state
    }

    override fun getSecurityStateFlow(): StateFlow<SecurityState> = stateEngine.securityState
    override fun getSecurityWarningFlow(): StateFlow<SecurityWarning?> = stateEngine.currentWarning
    override fun getThreatFlow(): StateFlow<SecurityThreat?> = stateEngine.currentThreat
    override fun getSslWarningFlow(): StateFlow<SslWarningState> = stateEngine.sslWarningState
    override fun getSecurityErrorFlow(): StateFlow<SecurityError?> = stateEngine.currentError

    override fun clearSecurityWarning() {
        actionController.blockSslWarning("")
    }

    override fun proceedSslWarning(url: String) {
        actionController.proceedSslWarning(url, isPrivate = false)
    }

    override fun proceedSslWarning(url: String, isPrivate: Boolean) {
        actionController.proceedSslWarning(url, isPrivate = isPrivate)
    }

    override fun blockSslWarning(url: String) {
        actionController.blockSslWarning(url, isPrivate = false)
    }

    override fun blockSslWarning(url: String, isPrivate: Boolean) {
        actionController.blockSslWarning(url, isPrivate = isPrivate)
    }

    override fun whitelistDomain(host: String) {
        sslPolicyEngine.whitelistDomain(host)
        diagnosticsEngine.logEvent("Whitelisted domain: $host")
    }

    override fun unwhitelistDomain(host: String) {
        sslPolicyEngine.unwhitelistDomain(host)
        diagnosticsEngine.logEvent("Unwhitelisted domain: $host")
    }

    override fun analyzeDownloadSafety(url: String, contentDisposition: String?, mimeType: String?): Boolean {
        return actionController.analyzeDownloadSafety(url, contentDisposition, mimeType, isPrivate = false)
    }

    override fun analyzeDownloadSafety(url: String, contentDisposition: String?, mimeType: String?, isPrivate: Boolean): Boolean {
        return actionController.analyzeDownloadSafety(url, contentDisposition, mimeType, isPrivate = isPrivate)
    }

    override fun isSubresourceSafe(requestUrl: String, documentUrl: String?, resourceType: String?): Boolean {
        return isSubresourceSafe(requestUrl, documentUrl, resourceType, isPrivate = false)
    }

    override fun isSubresourceSafe(requestUrl: String, documentUrl: String?, resourceType: String?, isPrivate: Boolean): Boolean {
        if (SecurityUtils.isLocalOrInternalUrl(requestUrl)) {
            return true
        }
        return try {
            val threat = threatDetectionEngine.detectThreat(requestUrl, isPrivate)
            if (threat != null) {
                diagnosticsEngine.logEvent("Subresource blocked due to threat: ${threat.category} in $requestUrl", isPrivate)
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subresource check error (failing open): ${e.message}")
            true
        }
    }

    override fun refreshSecurityState() {
        diagnosticsEngine.logEvent("Security state refreshed")
    }

    override fun saveSecuritySession() {
        lifecycleController.saveSession()
    }

    override fun restoreSecuritySession(): SecuritySession? {
        return lifecycleController.restoreSession()
    }

    override fun getSecurityDiagnosticsFlow(): StateFlow<String> = diagnosticsEngine.diagnosticsFlow
    override fun getEphemeralSecurityDiagnosticsFlow(): StateFlow<String> = diagnosticsEngine.ephemeralPrivateDiagnostics
}

object SecurityEngineProvider {
    val api: SecurityEngineApi get() = SwiftSecurityEngine
}

class SafeBrowsing : SecurityEngine {
    override fun isUrlSafe(url: String): Boolean = SwiftSecurityEngine.isUrlSafe(url)
    override fun checkCertificate(url: String): CertificateCheckResult = SwiftSecurityEngine.checkCertificate(url)
    override fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?): Boolean =
        SwiftSecurityEngine.isDownloadSafe(url, contentDisposition, mimeType)
}
