package com.swift.browser.vpnengine.data.provider

import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnProvider
import com.swift.browser.vpnengine.data.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import android.util.Log

class VpnGateProvider : ServerProviderLayer {
    override suspend fun fetchProviders(): List<VpnProvider> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<VpnServer>()
        try {
            val url = URL("http://www.vpngate.net/api/iphone/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val csv = connection.inputStream.bufferedReader().readText()
                val lines = csv.split("\n").filter { it.isNotBlank() && !it.startsWith("*") && !it.startsWith("#") }
                
                for (line in lines) {
                    val parts = line.split(",")
                    if (parts.size >= 15) {
                        val hostName = parts[0]
                        val ip = parts[1]
                        val score = parts[2].toFloatOrNull() ?: 0f
                        val ping = parts[3].toIntOrNull() ?: -1
                        val speed = parts[4].toLongOrNull() ?: 0L
                        val countryLong = parts[5]
                        val numVpnSessions = parts[7].toIntOrNull() ?: 0
                        val uptime = parts[8].toLongOrNull() ?: 0L
                        val operator = parts[12]
                        val base64Config = parts[14]

                        val speedMbps = (speed / 1000_000).toInt()
                        
                        val normalizedScore = (score / 1_000_000f).coerceIn(1f, 5f)

                        servers.add(
                            VpnServer(
                                id = UUID.randomUUID().toString(),
                                name = hostName,
                                country = countryLong,
                                city = ip,
                                protocol = VpnProtocol.OPENVPN,
                                ping = ping,
                                load = numVpnSessions,
                                speed = speedMbps,
                                isFree = true,
                                providerName = "VPN Gate",
                                qualityScore = normalizedScore,
                                tags = listOf("VPN Gate", "OpenVPN", operator).filter { it.isNotBlank() }, openVpnConfigBase64 = base64Config
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VpnGateProvider", "Failed to fetch VPN Gate servers", e)
        }

        if (servers.isEmpty()) {
            return@withContext emptyList()
        }

        listOf(
            VpnProvider(
                id = "vpngate",
                name = "VPN Gate Public Relay Servers",
                servers = servers
            )
        )
    }
}
