package com.swift.browser.vpnengine.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnAccountType {
    GUEST,
    LOCAL_PROFILE,
    SWIFT_ACCOUNT
}

data class VpnUserProfile(
    val accountId: String,
    val type: VpnAccountType,
    val name: String,
    val isCloudSyncEnabled: Boolean
)

class VpnAccountManager {
    private val _currentUser = MutableStateFlow(
        VpnUserProfile(
            accountId = "guest_001",
            type = VpnAccountType.GUEST,
            name = "Guest User",
            isCloudSyncEnabled = false
        )
    )
    val currentUser: StateFlow<VpnUserProfile> = _currentUser.asStateFlow()

    fun switchToLocalProfile(name: String) {
        _currentUser.value = VpnUserProfile(
            accountId = "local_${System.currentTimeMillis()}",
            type = VpnAccountType.LOCAL_PROFILE,
            name = name,
            isCloudSyncEnabled = false
        )
    }

    fun loginSwiftAccount(email: String) {
        _currentUser.value = VpnUserProfile(
            accountId = "swift_${email.hashCode()}",
            type = VpnAccountType.SWIFT_ACCOUNT,
            name = email,
            isCloudSyncEnabled = true
        )
    }

    fun logout() {
        _currentUser.value = VpnUserProfile(
            accountId = "guest_001",
            type = VpnAccountType.GUEST,
            name = "Guest User",
            isCloudSyncEnabled = false
        )
    }
}
