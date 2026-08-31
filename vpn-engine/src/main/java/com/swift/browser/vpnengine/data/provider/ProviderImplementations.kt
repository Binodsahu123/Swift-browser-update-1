package com.swift.browser.vpnengine.data.provider

import com.swift.browser.vpnengine.data.model.VpnProfile
import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnServer
import com.swift.browser.vpnengine.domain.VpnProviderAdapter
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class MockWireguardProvider : VpnProviderAdapter {
    override val providerId: String = "mock_wg"
    override val providerName: String = "WireGuard Internal"
    override val supportedProtocols: List<VpnProtocol> = listOf(VpnProtocol.WIREGUARD)

override suspend fun getServers(): List<VpnServer> {
        return listOf(
            VpnServer(UUID.randomUUID().toString(), "US East", "USA", "New York", VpnProtocol.WIREGUARD, 45, 30, 100, false, providerName, qualityScore = 4.5f, tags = listOf("Gaming", "Streaming", "Privacy")),
            VpnServer(UUID.randomUUID().toString(), "UK London", "UK", "London", VpnProtocol.WIREGUARD, 85, 45, 100, false, providerName, qualityScore = 4.0f, tags = listOf("Streaming", "Privacy"))
        )
    }

    override suspend fun parseConfig(file: File): VpnProfile? {
        if (!file.name.endsWith(".conf")) return null
        return VpnProfile(
            name = file.nameWithoutExtension,
            providerId = providerId,
            serverId = "custom",
            country = "Unknown",
            protocol = VpnProtocol.WIREGUARD,
            configData = file.readText(),
            configPath = file.absolutePath
        )
    }

    override suspend fun connect(profile: VpnProfile): Boolean {
        delay(1000)
        return true
    }

    override suspend fun disconnect(): Boolean {
        delay(500)
        return true
    }
}
