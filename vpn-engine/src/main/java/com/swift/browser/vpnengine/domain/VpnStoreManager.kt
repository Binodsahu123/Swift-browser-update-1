package com.swift.browser.vpnengine.domain

enum class ProviderCategory {
    IMPORTED_PROFILES,
    SWIFT_VPN,
    PARTNER_PROVIDERS,
    ENTERPRISE_PROFILES,
    DOWNLOADED_PROFILES
}

data class StoreItem(
    val id: String,
    val name: String,
    val description: String,
    val category: ProviderCategory,
    val isInstalled: Boolean,
    val requiresSubscription: Boolean
)

class VpnStoreManager {
    fun getAvailableProviders(): List<StoreItem> {
        return listOf(
            StoreItem("swift_vpn", "Swift VPN", "Native high-speed network", ProviderCategory.SWIFT_VPN, true, true),
            StoreItem("partner_proton", "ProtonVPN (Partner)", "Secure partner network", ProviderCategory.PARTNER_PROVIDERS, false, false),
            StoreItem("partner_windscribe", "Windscribe (Partner)", "Reliable partner network", ProviderCategory.PARTNER_PROVIDERS, false, false),
            StoreItem("enterprise_corp", "Corporate Intranet", "Company internal network", ProviderCategory.ENTERPRISE_PROFILES, false, true),
            StoreItem("imported_custom", "Custom Profiles", "Your imported OVPN/Conf files", ProviderCategory.IMPORTED_PROFILES, true, false)
        )
    }
}
