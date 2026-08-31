package com.swift.browser.vpnengine.data.provider

import com.swift.browser.vpnengine.data.model.VpnProfile
import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnServer
import com.swift.browser.vpnengine.domain.VpnProviderAdapter
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class WireGuardAdapter : VpnProviderAdapter {
    override val providerId: String = "wireguard"
    override val providerName: String = "WireGuard Profile"
    override val supportedProtocols: List<VpnProtocol> = listOf(VpnProtocol.WIREGUARD)

    override suspend fun getServers(): List<VpnServer> {
        return emptyList()
    }

    override suspend fun parseConfig(file: File): VpnProfile? {
        if (!file.exists() || !file.name.endsWith(".conf", ignoreCase = true)) {
            return null
        }
        val content = file.readText()
        val name = file.nameWithoutExtension

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            providerId = providerId,
            serverId = "wg_${UUID.randomUUID()}",
            country = "Custom",
            city = "Imported",
            protocol = VpnProtocol.WIREGUARD,
            configData = content,
            configPath = file.absolutePath
        )
    }

    override suspend fun connect(profile: VpnProfile): Boolean {
        delay(300) 
        return true
    }

    override suspend fun disconnect(): Boolean {
        delay(100)
        return true
    }
}
