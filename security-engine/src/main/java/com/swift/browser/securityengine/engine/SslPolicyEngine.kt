package com.swift.browser.securityengine.engine

import android.util.Log
import com.swift.browser.securityengine.CertificateCheckResult
import com.swift.browser.securityengine.manager.SecurityRepositoryManager
import com.swift.browser.securityengine.util.SecurityUtils
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

class SslPolicyEngine(
    private val repoManager: SecurityRepositoryManager
) {
    private companion object {
        const val TAG = "SslPolicyEngine"
    }

    fun whitelistDomain(host: String) {
        repoManager.whitelistDomain(host)
    }

    fun unwhitelistDomain(host: String) {
        repoManager.unwhitelistDomain(host)
    }

    fun isSslWhitelisted(host: String): Boolean {
        return repoManager.isDomainWhitelisted(host)
    }

    fun checkCertificate(url: String): CertificateCheckResult {
        if (!url.startsWith("https://")) {
            return CertificateCheckResult(isValid = false, error = "Insecure connection (HTTP)")
        }
        return try {
            val destinationUrl = URL(url)
            val connection = destinationUrl.openConnection() as HttpsURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()

            val certs = connection.serverCertificates
            if (certs.isNotEmpty() && certs[0] is X509Certificate) {
                val x509 = certs[0] as X509Certificate
                x509.checkValidity()
                CertificateCheckResult(
                    isValid = true,
                    subject = x509.subjectDN.name,
                    issuer = x509.issuerDN.name
                )
            } else {
                CertificateCheckResult(isValid = false, error = "No valid SSL X509 certificates received.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSL Certificate check failed: ${e.message}")
            CertificateCheckResult(isValid = false, error = e.localizedMessage ?: "Unknown certificate exception")
        }
    }
}
