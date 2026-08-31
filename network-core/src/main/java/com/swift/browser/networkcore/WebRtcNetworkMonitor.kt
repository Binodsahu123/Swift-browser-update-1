package com.swift.browser.networkcore

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WebRtcNetworkType {
    WiFi,
    CELLULAR,
    NONE,
    OTHER_UNKNOWN
}

enum class WebRtcNetworkState {
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED
}

object WebRtcNetworkMonitor {
    private const val TAG = "WebRtcNetworkMonitor"

    private val _networkType = MutableStateFlow<WebRtcNetworkType>(WebRtcNetworkType.NONE)
    val networkType = _networkType.asStateFlow()

    private val _networkState = MutableStateFlow<WebRtcNetworkState>(WebRtcNetworkState.DISCONNECTED)
    val networkState = _networkState.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    interface NetworkObserver {
        fun onNetworkChanged(type: WebRtcNetworkType, state: WebRtcNetworkState)
    }

    private val observers = mutableListOf<NetworkObserver>()

    fun registerObserver(observer: NetworkObserver) {
        synchronized(observers) {
            observers.add(observer)
            observer.onNetworkChanged(_networkType.value, _networkState.value)
        }
    }

    fun unregisterObserver(observer: NetworkObserver) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    fun startMonitoring(context: Context) {
        synchronized(this) {
            if (connectivityManager != null) return
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm

            // Determine initial network state
            updateNetworkInfo(cm.activeNetwork, cm)

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network onAvailable: $network")
                    updateNetworkInfo(network, cm, WebRtcNetworkState.CONNECTED)
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network onLost: $network")
                    val active = cm.activeNetwork
                    if (active == null || active == network) {
                        setNetworkState(WebRtcNetworkType.NONE, WebRtcNetworkState.DISCONNECTED)
                    } else {
                        updateNetworkInfo(active, cm)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "Network onCapabilitiesChanged: $network")
                    updateNetworkInfo(network, cm)
                }
            }

            try {
                cm.registerNetworkCallback(request, callback)
                networkCallback = callback
                Log.i(TAG, "Successfully started network monitoring.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register network callback", e)
            }
        }
    }

    fun stopMonitoring() {
        synchronized(this) {
            val cm = connectivityManager
            val cb = networkCallback
            if (cm != null && cb != null) {
                try {
                    cm.unregisterNetworkCallback(cb)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister network callback", e)
                }
            }
            connectivityManager = null
            networkCallback = null
        }
    }

    private fun updateNetworkInfo(network: Network?, cm: ConnectivityManager, forcedState: WebRtcNetworkState? = null) {
        if (network == null) {
            setNetworkState(WebRtcNetworkType.NONE, WebRtcNetworkState.DISCONNECTED)
            return
        }
        val capabilities = cm.getNetworkCapabilities(network)
        val type = when {
            capabilities == null -> WebRtcNetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WebRtcNetworkType.WiFi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> WebRtcNetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> WebRtcNetworkType.WiFi
            else -> WebRtcNetworkType.OTHER_UNKNOWN
        }

        val state = forcedState ?: if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            WebRtcNetworkState.CONNECTED
        } else {
            WebRtcNetworkState.DISCONNECTED
        }

        setNetworkState(type, state)
    }

    fun setNetworkState(type: WebRtcNetworkType, state: WebRtcNetworkState) {
        val oldType = _networkType.value
        val oldState = _networkState.value
        _networkType.value = type
        _networkState.value = state

        if (oldType != type || oldState != state) {
            Log.i(TAG, "Network Transition: $oldType -> $type, State: $oldState -> $state")
            val observersCopy = synchronized(observers) { observers.toList() }
            observersCopy.forEach { it.onNetworkChanged(type, state) }
        }
    }
}
