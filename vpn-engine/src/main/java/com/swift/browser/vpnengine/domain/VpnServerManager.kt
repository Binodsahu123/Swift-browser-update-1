package com.swift.browser.vpnengine.domain

import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnServer
import com.swift.browser.vpnengine.data.provider.ServerProviderLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnServerManager(private val providerLayer: ServerProviderLayer) {
    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private val _favoriteServerIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteServerIds: StateFlow<Set<String>> = _favoriteServerIds.asStateFlow()

    private val _recentServerIds = MutableStateFlow<List<String>>(emptyList())
    val recentServerIds: StateFlow<List<String>> = _recentServerIds.asStateFlow()

    suspend fun refreshServers(hasInternet: Boolean = true) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _refreshError.value = null

        try {
            if (!hasInternet) {
                _refreshError.value = "Offline Mode"
                delay(500) // Simulate local cache loading
            } else {
                delay(800) // Show smooth animation
                val providers = providerLayer.fetchProviders()
                val newServers = providers.flatMap { it.servers }
                if (newServers.isNotEmpty()) {
                    _servers.value = newServers
                    _lastUpdated.value = System.currentTimeMillis()
                } else {
                    _refreshError.value = "No servers received"
                }
            }
        } catch (e: Exception) {
            _refreshError.value = "Unable to refresh. Displaying cached servers."
        } finally {
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(serverId: String) {
        val current = _favoriteServerIds.value.toMutableSet()
        if (current.contains(serverId)) {
            current.remove(serverId)
        } else {
            current.add(serverId)
        }
        _favoriteServerIds.value = current
    }

    fun addRecent(serverId: String) {
        val current = _recentServerIds.value.toMutableList()
        current.remove(serverId)
        current.add(0, serverId)
        if (current.size > 5) {
            current.removeLast()
        }
        _recentServerIds.value = current
    }

fun getServersByCountry(country: String): List<VpnServer> {
        return _servers.value.filter { it.country == country }
    }

    fun getServersByProtocol(protocol: VpnProtocol): List<VpnServer> {
        return _servers.value.filter { it.protocol == protocol }
    }

    fun getFastestServer(): VpnServer? {
        return _servers.value.minByOrNull { it.ping }
    }

    fun getBestServerRecommendation(): VpnServer? {
        return _servers.value.maxByOrNull { it.qualityScore } ?: _servers.value.minByOrNull { it.ping + (it.load * 2) }
    }

    fun getServersByTag(tag: String): List<VpnServer> {
        return _servers.value.filter { it.tags.contains(tag) }.sortedByDescending { it.qualityScore }
    }

    fun setCustomLabel(serverId: String, label: String) {
        val updated = _servers.value.map { if (it.id == serverId) it.copy(customLabel = label) else it }
        _servers.value = updated
    }

    fun searchServers(query: String): List<VpnServer> {
        val q = query.lowercase()
        return _servers.value.filter { 
            it.name.lowercase().contains(q) || 
            it.country.lowercase().contains(q) || 
            it.city.lowercase().contains(q) || 
            it.providerName.lowercase().contains(q) ||
            it.protocol.name.lowercase().contains(q) ||
            it.customLabel?.lowercase()?.contains(q) == true
        }
    }

    fun getUniqueCountries(): List<String> {
        return _servers.value.map { it.country }.distinct().sorted()
    }
}
