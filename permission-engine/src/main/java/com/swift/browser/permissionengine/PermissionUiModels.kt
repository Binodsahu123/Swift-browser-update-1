package com.swift.browser.permissionengine

data class PermissionItemUiState(
    val permissionType: String,
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val decision: String, // "Ask", "Allow", "Block", "Session-only"
    val isTemporary: Boolean = false,
    val expiresAt: Long? = null,
    val androidState: String? = null,
    val webViewState: String? = null,
    val supportLabel: String? = null, // "UNSUPPORTED", "NOT AVAILABLE", null
    val riskLevel: String = "Medium",
    val canChange: Boolean = true,
    val canReset: Boolean = true
)

data class SitePermissionUiState(
    val origin: String,
    val displayName: String,
    val icon: String? = null,
    val permissions: List<PermissionItemUiState>,
    val secure: Boolean = true,
    val connectionState: String = "Secure",
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PermissionCenterUiState(
    val sites: List<SitePermissionUiState> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)
