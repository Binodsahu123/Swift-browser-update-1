package com.swift.browser.vpnengine.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VpnConnectionState {
    IDLE,
    PERMISSION,
    IMPORTING,
    PARSING,
    READY,
    CONNECTING,
    AUTHENTICATING,
    TUNNEL_READY,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    RECOVERING,
    FAILED
}

data class VpnSettings(
    val autoConnect: Boolean = false,
    val connectOnWifi: Boolean = false,
    val connectOnMobile: Boolean = false,
    val splitTunneling: Boolean = false,
    val killSwitch: Boolean = false,
    val alwaysOn: Boolean = false,
    val notifications: Boolean = true,
    val theme: String = "System",
    val selectedDns: String = "Default",
    val autoRefreshInterval: Long = 0L,
    val refreshOnAppOpen: Boolean = true,
    val refreshOnVpnScreenOpen: Boolean = true,
    val refreshOnWifiOnly: Boolean = false,
    val backgroundRefresh: Boolean = false,
    val connectionMode: com.swift.browser.vpnengine.data.model.VpnConnectionMode = com.swift.browser.vpnengine.data.model.VpnConnectionMode.NORMAL
)

data class VpnTrafficStats(
    val downloadSpeedBytes: Long = 0,
    val uploadSpeedBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    val totalUploadBytes: Long = 0,
    val latencyMs: Int = 0
)

data class VpnLogEntry(
    val timestamp: Long,
    val message: String,
    val type: String // INFO, ERROR, WARN
)

data class VpnSessionStats(
    val connectedSince: Long = 0,
    val durationSeconds: Long = 0,
    val reconnectCount: Int = 0
)


data class VpnHealthStats(
    val status: String = "Excellent", // Excellent, Good, Fair, Poor, Critical
    val ping: Int = 0,
    val packetLoss: Float = 0f,
    val stability: Float = 1.0f,
    val dnsResponseTime: Int = 0,
    val tunnelHealth: String = "Stable"
)

data class VpnSecurityStatus(
    val dnsLeakProtected: Boolean = true,
    val ipv6LeakProtected: Boolean = true,
    val webrtcLeakProtected: Boolean = true,
    val encryptionActive: Boolean = false,
    val overallScore: Int = 100
)

data class AiRecommendation(
    val bestCountry: String = "Optimal",
    val bestServerId: String? = null,
    val bestProtocol: String = "WireGuard",
    val bestDns: String = "Cloudflare",
    val bestMode: String = "Balanced"
)

class VpnStateManager {

    private val _connectionState = MutableStateFlow(VpnConnectionState.IDLE)
    val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

    private val _connectedServerId = MutableStateFlow<String?>(null)
    val connectedServerId: StateFlow<String?> = _connectedServerId.asStateFlow()

    private val _settings = MutableStateFlow(VpnSettings())
    val settings: StateFlow<VpnSettings> = _settings.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _trafficStats = MutableStateFlow(VpnTrafficStats())
    val trafficStats: StateFlow<VpnTrafficStats> = _trafficStats.asStateFlow()

    private val _sessionStats = MutableStateFlow(VpnSessionStats())
    val sessionStats: StateFlow<VpnSessionStats> = _sessionStats.asStateFlow()

    private val _logs = MutableStateFlow<List<VpnLogEntry>>(emptyList())
    val logs: StateFlow<List<VpnLogEntry>> = _logs.asStateFlow()
    
private val _networkQuality = MutableStateFlow("Average")
    val networkQuality: StateFlow<String> = _networkQuality.asStateFlow()

    private val _healthStats = MutableStateFlow(VpnHealthStats())
    val healthStats: StateFlow<VpnHealthStats> = _healthStats.asStateFlow()

    private val _securityStatus = MutableStateFlow(VpnSecurityStatus())
    val securityStatus: StateFlow<VpnSecurityStatus> = _securityStatus.asStateFlow()

    private val _aiRecommendation = MutableStateFlow(AiRecommendation())
    val aiRecommendation: StateFlow<AiRecommendation> = _aiRecommendation.asStateFlow()

    init {
        addLog("VpnStateManager initialized")
    }

    fun setConnectionState(state: VpnConnectionState, serverId: String? = null) {
        val prevState = _connectionState.value
        _connectionState.value = state
        
        if (state == VpnConnectionState.CONNECTED && prevState != VpnConnectionState.CONNECTED) {
            _sessionStats.value = _sessionStats.value.copy(connectedSince = System.currentTimeMillis(), durationSeconds = 0)
        }
        
        if (serverId != null && (state == VpnConnectionState.CONNECTED || state == VpnConnectionState.CONNECTING)) {
            _connectedServerId.value = serverId
        }

        if (state == VpnConnectionState.DISCONNECTED || state == VpnConnectionState.FAILED) {
            // Keep the last connected ID so UI knows what we disconnected from, but maybe reset stats
            if (state == VpnConnectionState.DISCONNECTED) {
                addLog("VPN Disconnected")
            }
        }
        addLog("State changed to ${state.name}")
    }

    fun setError(message: String) {
        _errorMessage.value = message
        setConnectionState(VpnConnectionState.FAILED)
        addLog("Error: $message", "ERROR")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateSettings(newSettings: VpnSettings) {
        _settings.value = newSettings
        addLog("Settings updated")
    }

    fun updateTrafficStats(stats: VpnTrafficStats) {
        _trafficStats.value = stats
        
        // update network quality roughly based on latency
        val quality = when {
            stats.latencyMs < 50 -> "Excellent"
            stats.latencyMs < 120 -> "Good"
            stats.latencyMs < 250 -> "Average"
            else -> "Poor"
        }
        if (_networkQuality.value != quality) {
            _networkQuality.value = quality
        }
    }

    fun incrementDuration(seconds: Long = 1) {
        if (_connectionState.value == VpnConnectionState.CONNECTED) {
            _sessionStats.value = _sessionStats.value.copy(
                durationSeconds = _sessionStats.value.durationSeconds + seconds
            )
        }
    }

    fun incrementReconnect() {
        _sessionStats.value = _sessionStats.value.copy(
            reconnectCount = _sessionStats.value.reconnectCount + 1
        )
    }

    fun addLog(message: String, type: String = "INFO") {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, VpnLogEntry(System.currentTimeMillis(), message, type))
        if (currentLogs.size > 200) {
            currentLogs.removeLast()
        }
        _logs.value = currentLogs
    }
    
fun clearLogs() {
        _logs.value = emptyList()
    }

    fun updateHealthStats(stats: VpnHealthStats) {
        _healthStats.value = stats
    }
    
    fun updateSecurityStatus(status: VpnSecurityStatus) {
        _securityStatus.value = status
    }
    
    fun updateAiRecommendation(rec: AiRecommendation) {
        _aiRecommendation.value = rec
    }
}
