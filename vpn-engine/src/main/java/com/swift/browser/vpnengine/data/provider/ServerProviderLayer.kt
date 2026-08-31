package com.swift.browser.vpnengine.data.provider

import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnProvider
import com.swift.browser.vpnengine.data.model.VpnServer
import kotlinx.coroutines.delay
import java.util.UUID

interface ServerProviderLayer {
    suspend fun fetchProviders(): List<VpnProvider>
}

class MockServerProvider : ServerProviderLayer {
    override suspend fun fetchProviders(): List<VpnProvider> {
        // Removed artificial delay
        
        fun createServer(id: String, name: String, country: String, city: String, protocol: VpnProtocol, ping: Int, load: Int, speed: Int, isFree: Boolean, providerName: String): VpnServer {
            val score = 5.0f - (ping / 100f) - (load / 100f)
            val finalScore = score.coerceIn(1.0f, 5.0f)
            val tags = mutableListOf<String>()
            if (ping < 50 && load < 50) tags.add("Gaming")
            if (speed > 80 && load < 60) tags.add("Streaming")
            if (speed > 100) tags.add("Downloads")
            if (protocol == VpnProtocol.OPENVPN || protocol == VpnProtocol.WIREGUARD) tags.add("Privacy")
            tags.add("Browsing")
            
            return VpnServer(
                id = id,
                name = name,
                country = country,
                city = city,
                protocol = protocol,
                ping = ping,
                load = load,
                speed = speed,
                isFree = isFree,
                providerName = providerName,
                qualityScore = finalScore,
                tags = tags
            )
        }
        
        return listOf(
            VpnProvider(id = UUID.randomUUID().toString(), 
                name = "ProtonVPN (Free Tier)",
                servers = listOf(
                    createServer(UUID.randomUUID().toString(), "US-FREE#1", "USA", "New York", VpnProtocol.WIREGUARD, 45, 60, 50, true, "ProtonVPN"),
                    createServer(UUID.randomUUID().toString(), "NL-FREE#2", "Netherlands", "Amsterdam", VpnProtocol.OPENVPN, 110, 80, 40, true, "ProtonVPN"),
                    createServer(UUID.randomUUID().toString(), "JP-FREE#3", "Japan", "Tokyo", VpnProtocol.WIREGUARD, 150, 40, 30, true, "ProtonVPN")
                )
            ),
            VpnProvider(id = UUID.randomUUID().toString(), 
                name = "Windscribe",
                servers = listOf(
                    createServer(UUID.randomUUID().toString(), "US Central", "USA", "Dallas", VpnProtocol.WIREGUARD, 30, 20, 100, true, "Windscribe"),
                    createServer(UUID.randomUUID().toString(), "UK London", "UK", "London", VpnProtocol.OPENVPN, 80, 50, 80, true, "Windscribe")
                )
            ),
            VpnProvider(id = UUID.randomUUID().toString(), 
                name = "Premium Providers",
                servers = listOf(
                    createServer(UUID.randomUUID().toString(), "CH-Premium", "Switzerland", "Zurich", VpnProtocol.WIREGUARD, 20, 10, 500, false, "Premium"),
                    createServer(UUID.randomUUID().toString(), "SG-Premium", "Singapore", "Singapore", VpnProtocol.WIREGUARD, 15, 5, 1000, false, "Premium")
                )
            )
        )
    }
}
