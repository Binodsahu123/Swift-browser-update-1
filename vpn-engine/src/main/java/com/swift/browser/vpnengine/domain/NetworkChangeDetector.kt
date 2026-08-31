package com.swift.browser.vpnengine.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.swift.browser.vpnengine.VpnEngineDependencyContainer

class NetworkChangeDetector(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isListening = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            val type = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                else -> "Unknown Network"
            }
            Log.d("NetworkChangeDetector", "Network Available: $type")
            VpnEngineDependencyContainer.stateManager.addLog("Network connection established: $type", "INFO")
            
            // Check if we were disconnected and autoConnect is on
            val settings = VpnEngineDependencyContainer.stateManager.settings.value
            val connectionState = VpnEngineDependencyContainer.stateManager.connectionState.value
            
            if (connectionState == VpnConnectionState.DISCONNECTED && settings.autoConnect) {
                VpnEngineDependencyContainer.stateManager.addLog("Auto-Connect triggered by network change", "INFO")
                // A real implementation would trigger SwiftVpnService.ACTION_CONNECT here.
            }
        }

        override fun onLost(network: Network) {
            Log.w("NetworkChangeDetector", "Network Lost")
            VpnEngineDependencyContainer.stateManager.addLog("Network connection lost", "WARN")
            
            // If VPN is connected and kill switch is enabled, we might want to block traffic,
            // but Android's always-on VPN usually handles this.
        }
    }

    fun startListening() {
        if (isListening) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        isListening = true
        Log.d("NetworkChangeDetector", "Started listening for network changes")
    }

    fun stopListening() {
        if (!isListening) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        isListening = false
        Log.d("NetworkChangeDetector", "Stopped listening for network changes")
    }
}
