package com.swift.browser.vpnengine.domain

import com.swift.browser.vpnengine.data.model.VpnProfile
import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnProvider
import com.swift.browser.vpnengine.data.model.VpnServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

interface VpnProviderAdapter {
    val providerId: String
    val providerName: String
    val supportedProtocols: List<VpnProtocol>
    
    suspend fun getServers(): List<VpnServer>
    suspend fun parseConfig(file: File): VpnProfile?
    suspend fun connect(profile: VpnProfile): Boolean
    suspend fun disconnect(): Boolean
}

class ProviderManager {
    private val providers = mutableMapOf<String, VpnProviderAdapter>()
    
    private val _profiles = MutableStateFlow<List<VpnProfile>>(emptyList())
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    fun registerProvider(adapter: VpnProviderAdapter) {
        providers[adapter.providerId] = adapter
    }

    fun getProvider(id: String): VpnProviderAdapter? = providers[id]
    
    fun getAllProviders(): List<VpnProviderAdapter> = providers.values.toList()

    fun addProfile(profile: VpnProfile) {
        val current = _profiles.value.toMutableList()
        current.add(profile)
        _profiles.value = current
    }

    fun removeProfile(profileId: String) {
        val current = _profiles.value.toMutableList()
        current.removeAll { it.id == profileId }
        _profiles.value = current
    }
    
    fun updateProfile(profile: VpnProfile) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            current[index] = profile
            _profiles.value = current
        }
    }
}
