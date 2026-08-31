package com.swift.browser.adblockengine.network

/**
 * Utility determining if a given request is cross-origin / third-party to the active viewport host.
 */
object AdBlockThirdPartyDetector {
    fun isThirdParty(requestUrl: String, documentUrl: String?): Boolean {
        if (documentUrl == null) return false
        val reqHost = getHost(requestUrl) ?: return false
        val docHost = getHost(documentUrl) ?: return false
        
        return !reqHost.endsWith(docHost) && !docHost.endsWith(reqHost)
    }

    private fun getHost(url: String): String? {
        return try {
            android.net.Uri.parse(url).host?.lowercase()
        } catch (e: Exception) {
            null
        }
    }
}
