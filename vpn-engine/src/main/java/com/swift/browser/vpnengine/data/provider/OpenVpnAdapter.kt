package com.swift.browser.vpnengine.data.provider

import com.swift.browser.vpnengine.data.model.VpnProfile
import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnServer
import com.swift.browser.vpnengine.domain.VpnProviderAdapter
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class OpenVpnAdapter : VpnProviderAdapter {
    override val providerId: String = "openvpn"
    override val providerName: String = "OpenVPN Profile"
    override val supportedProtocols: List<VpnProtocol> = listOf(VpnProtocol.OPENVPN, VpnProtocol.OPENVPN)

    override suspend fun getServers(): List<VpnServer> {
        return emptyList()
    }

    override suspend fun parseConfig(file: File): VpnProfile? {
        if (!file.exists() || !file.name.endsWith(".ovpn", ignoreCase = true)) {
            return null
        }
        val content = file.readText()
        val isTcp = content.contains("proto tcp", ignoreCase = true)
        val protocol = if (isTcp) VpnProtocol.OPENVPN else VpnProtocol.OPENVPN
        val name = file.nameWithoutExtension

        return VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            providerId = providerId,
            serverId = "ovpn_${UUID.randomUUID()}",
            country = "Custom",
            city = "Imported",
            protocol = protocol,
            configData = content,
            configPath = file.absolutePath
        )
    }

    override suspend fun connect(profile: VpnProfile): Boolean {
        delay(800)
        return true
    }

    override suspend fun disconnect(): Boolean {
        delay(200)
        return true
    }
}
