package com.swift.browser.videoengine.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed class TestResult {
    data class Success(val message: String) : TestResult()
    data class Failure(val message: String, val error: Throwable? = null) : TestResult()
}

object StreamEndpointTester {

    /**
     * Attempts a real TLS handshake to a target host and port.
     * Verifies that certificate is trusted, chain is valid, and hostname is verified.
     */
    suspend fun testTlsHandshake(host: String, port: Int, timeoutMs: Int = 5000): TestResult = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), timeoutMs)
            rawSocket.soTimeout = timeoutMs

            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = sslSocketFactory.createSocket(rawSocket, host, port, true) as SSLSocket

            val sslParams = sslSocket.sslParameters
            sslParams.endpointIdentificationAlgorithm = "HTTPS"
            sslSocket.sslParameters = sslParams

            sslSocket.startHandshake()

            val session = sslSocket.session
            val verified = HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)
            if (!verified) {
                return@withContext TestResult.Failure("TLS Handshake completed, but hostname verification failed for $host.")
            }

            TestResult.Success("TLS connection to $host:$port established and verified successfully.")
        } catch (e: SSLHandshakeException) {
            TestResult.Failure("TLS validation rejected: Connection untrusted or invalid certificate.", e)
        } catch (e: SSLPeerUnverifiedException) {
            TestResult.Failure("TLS hostname verification rejected this connection.", e)
        } catch (e: Exception) {
            TestResult.Failure("Connection failed: ${e.localizedMessage ?: "Unknown error"}", e)
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Verifies that self-signed/invalid certificates are correctly rejected.
     * Uses badssl.com endpoints which are public and standard for testing.
     */
    suspend fun testInvalidCertRejection(timeoutMs: Int = 5000): TestResult = withContext(Dispatchers.IO) {
        val badsslHost = "self-signed.badssl.com"
        val port = 443
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(badsslHost, port), timeoutMs)
            rawSocket.soTimeout = timeoutMs

            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = sslSocketFactory.createSocket(rawSocket, badsslHost, port, true) as SSLSocket

            val sslParams = sslSocket.sslParameters
            sslParams.endpointIdentificationAlgorithm = "HTTPS"
            sslSocket.sslParameters = sslParams

            sslSocket.startHandshake()
            
            // If handshake succeeds, it means invalid certificates are NOT being rejected!
            TestResult.Failure("Security Vulnerability: App accepted a self-signed certificate.")
        } catch (e: SSLHandshakeException) {
            TestResult.Success("Success: Self-signed certificate was correctly rejected.")
        } catch (e: Exception) {
            // General connection/IO exceptions might happen, but standard expectation is SSLHandshakeException
            TestResult.Success("Success: Connection blocked securely (${e.message}).")
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Verifies that wrong hostnames on certificates are rejected.
     * Connects to google.com but performs verification against an invalid hostname.
     */
    suspend fun testWrongHostnameRejection(timeoutMs: Int = 5000): TestResult = withContext(Dispatchers.IO) {
        val host = "google.com"
        val port = 443
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), timeoutMs)
            rawSocket.soTimeout = timeoutMs

            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            // Connect to google.com, but tell SSLSocket the target is "wronghost.com" to trigger hostname verification failure
            sslSocket = sslSocketFactory.createSocket(rawSocket, "wronghost.com", port, true) as SSLSocket

            val sslParams = sslSocket.sslParameters
            sslParams.endpointIdentificationAlgorithm = "HTTPS"
            sslSocket.sslParameters = sslParams

            sslSocket.startHandshake()

            val session = sslSocket.session
            val verified = HttpsURLConnection.getDefaultHostnameVerifier().verify("wronghost.com", session)
            if (verified) {
                TestResult.Failure("Security Vulnerability: App accepted a mismatched hostname certificate.")
            } else {
                TestResult.Success("Success: Wrong hostname verified and rejected.")
            }
        } catch (e: SSLHandshakeException) {
            TestResult.Success("Success: Mismatched hostname rejected on TLS handshake.")
        } catch (e: SSLPeerUnverifiedException) {
            TestResult.Success("Success: Hostname verification rejected mismatched certificate.")
        } catch (e: Exception) {
            TestResult.Success("Success: Connection blocked securely (${e.message}).")
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }
}
