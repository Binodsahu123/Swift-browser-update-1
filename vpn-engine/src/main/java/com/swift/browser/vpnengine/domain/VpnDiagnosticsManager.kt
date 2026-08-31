package com.swift.browser.vpnengine.domain

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VpnDiagnosticsManager(private val context: Context) {
    fun generateDiagnosticsReport(logs: List<VpnLogEntry>, healthStats: VpnHealthStats, securityStatus: VpnSecurityStatus): File {
        val reportDir = File(context.filesDir, "vpn_diagnostics")
        if (!reportDir.exists()) reportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val reportFile = File(reportDir, "diagnostics_$timestamp.txt")

        val sb = StringBuilder()
        sb.appendLine("=== SWIFT BROWSER VPN DIAGNOSTICS ===")
        sb.appendLine("Date: ${Date()}")
        sb.appendLine("Engine Version: 1.2.0")
        sb.appendLine()
        
        sb.appendLine("--- HEALTH STATS ---")
        sb.appendLine("Status: ${healthStats.status}")
        sb.appendLine("Ping: ${healthStats.ping}ms")
        sb.appendLine("Packet Loss: ${healthStats.packetLoss}%")
        sb.appendLine("Stability: ${healthStats.stability}")
        sb.appendLine()

        sb.appendLine("--- SECURITY STATUS ---")
        sb.appendLine("Encryption Active: ${securityStatus.encryptionActive}")
        sb.appendLine("DNS Leak Protected: ${securityStatus.dnsLeakProtected}")
        sb.appendLine("IPv6 Protected: ${securityStatus.ipv6LeakProtected}")
        sb.appendLine("WebRTC Protected: ${securityStatus.webrtcLeakProtected}")
        sb.appendLine("Overall Score: ${securityStatus.overallScore}/100")
        sb.appendLine()

        sb.appendLine("--- LOGS ---")
        logs.forEach { log ->
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
            sb.appendLine("[$time] ${log.type}: ${log.message}")
        }

        reportFile.writeText(sb.toString())
        return reportFile
    }
}
