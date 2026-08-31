package com.swift.browser.securityengine.api

import com.swift.browser.securityengine.SslWarningState
import com.swift.browser.securityengine.model.*
import kotlinx.coroutines.flow.StateFlow

interface SecurityEngineApi {
    fun checkUrlSafety(url: String): Boolean
    fun checkUrlSafety(url: String, isPrivate: Boolean): Boolean = checkUrlSafety(url)
    fun checkSecurityState(url: String): SecurityState
    fun checkSecurityState(url: String, isPrivate: Boolean): SecurityState = checkSecurityState(url)
    fun getSecurityStateFlow(): StateFlow<SecurityState>
    fun getSecurityWarningFlow(): StateFlow<SecurityWarning?>
    fun getThreatFlow(): StateFlow<SecurityThreat?>
    fun getSslWarningFlow(): StateFlow<SslWarningState>
    fun getSecurityErrorFlow(): StateFlow<SecurityError?>
    fun clearSecurityWarning()
    fun proceedSslWarning(url: String)
    fun proceedSslWarning(url: String, isPrivate: Boolean) = proceedSslWarning(url)
    fun blockSslWarning(url: String)
    fun blockSslWarning(url: String, isPrivate: Boolean) = blockSslWarning(url)
    fun whitelistDomain(host: String)
    fun unwhitelistDomain(host: String)
    fun analyzeDownloadSafety(url: String, contentDisposition: String?, mimeType: String?): Boolean
    fun analyzeDownloadSafety(url: String, contentDisposition: String?, mimeType: String?, isPrivate: Boolean): Boolean = analyzeDownloadSafety(url, contentDisposition, mimeType)
    fun isSubresourceSafe(requestUrl: String, documentUrl: String?, resourceType: String? = null): Boolean
    fun isSubresourceSafe(requestUrl: String, documentUrl: String?, resourceType: String?, isPrivate: Boolean): Boolean = isSubresourceSafe(requestUrl, documentUrl, resourceType)
    fun refreshSecurityState()
    fun saveSecuritySession()
    fun restoreSecuritySession(): SecuritySession?
    fun getSecurityDiagnosticsFlow(): StateFlow<String>
    fun getEphemeralSecurityDiagnosticsFlow(): StateFlow<String>? = null
}
