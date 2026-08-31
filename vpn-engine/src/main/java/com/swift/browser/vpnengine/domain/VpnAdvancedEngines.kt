package com.swift.browser.vpnengine.domain

import com.swift.browser.vpnengine.data.model.VpnConnectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiAdvice(
    val title: String,
    val description: String,
    val suggestedAction: String? = null,
    val suggestedMode: VpnConnectionMode? = null
)

class AiVpnAssistant {
    private val _currentAdvice = MutableStateFlow<AiAdvice?>(null)
    val currentAdvice: StateFlow<AiAdvice?> = _currentAdvice.asStateFlow()

    fun analyzeFailure(errorCode: String) {
        val advice = when (errorCode) {
            "TIMEOUT" -> AiAdvice("Connection Timeout", "The server didn't respond in time. It might be offline.", "Try another server")
            "AUTH_FAILED" -> AiAdvice("Authentication Failed", "Your profile credentials might be invalid.", "Check Profile")
            "PERMISSION_DENIED" -> AiAdvice("Permission Required", "Android requires permission to establish a VPN.", "Grant Permission")
            else -> AiAdvice("Connection Failed", "An unknown error occurred.", "Retry")
        }
        _currentAdvice.value = advice
    }

    fun recommendOptimization(stats: VpnHealthStats, mode: VpnConnectionMode) {
        val advice = if (stats.ping > 200 && mode != VpnConnectionMode.GAMING) {
            AiAdvice("High Latency Detected", "Your ping is high. Switch to Gaming mode for lower latency.", "Enable Gaming Mode", VpnConnectionMode.GAMING)
        } else if (stats.packetLoss > 2.0f) {
            AiAdvice("Unstable Connection", "We detected packet loss. Consider switching servers for better stability.", "Switch Server")
        } else {
            null
        }
        _currentAdvice.value = advice
    }
    
    fun clearAdvice() {
        _currentAdvice.value = null
    }
}

data class DnsConfig(val id: String, val name: String, val primary: String, val secondary: String)

class DnsManager {
    val providers = listOf(
        DnsConfig("auto", "Automatic", "", ""),
        DnsConfig("cloudflare", "Cloudflare", "1.1.1.1", "1.0.0.1"),
        DnsConfig("google", "Google DNS", "8.8.8.8", "8.8.4.4"),
        DnsConfig("quad9", "Quad9", "9.9.9.9", "149.112.112.112")
    )
    
    private val _selectedDns = MutableStateFlow(providers.first())
    val selectedDns: StateFlow<DnsConfig> = _selectedDns
    
    fun setDns(configId: String) {
        val config = providers.find { it.id == configId } ?: providers.first()
        _selectedDns.value = config
    }
}





