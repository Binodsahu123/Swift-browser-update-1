package com.swift.browser.vpnengine.data.model

data class VpnServer(
    val id: String,
    val name: String,
    val country: String,
    val city: String,
    val protocol: VpnProtocol,
    val ping: Int,
    val load: Int,
    val speed: Int,
    val isFree: Boolean,
    val providerName: String,
    var isFavorite: Boolean = false,
    var isRecent: Boolean = false,
    var customLabel: String? = null,
    var qualityScore: Float = 0f,
    var tags: List<String> = emptyList(),
    var openVpnConfigBase64: String? = null
)

enum class VpnProtocol {
    WIREGUARD,
    OPENVPN,
    IKEV2,
    CUSTOM
}

data class VpnProvider(
    val id: String,
    val name: String,
    val servers: List<VpnServer>
)
