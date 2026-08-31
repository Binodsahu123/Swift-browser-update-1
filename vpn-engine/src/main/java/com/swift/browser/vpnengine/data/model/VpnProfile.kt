package com.swift.browser.vpnengine.data.model

import java.util.UUID

data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerId: String,
    val serverId: String,
    val country: String,
    val city: String = "",
    val protocol: VpnProtocol,
    val configData: String = "",
    val configPath: String = "",
    val importDate: Long = System.currentTimeMillis(),
    var lastConnected: Long = 0L,
    var isFavorite: Boolean = false,
    var status: String = "Idle"
)
