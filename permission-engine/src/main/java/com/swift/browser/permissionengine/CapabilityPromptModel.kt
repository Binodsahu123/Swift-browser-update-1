package com.swift.browser.permissionengine

data class CapabilityPromptItem(
    val capabilityId: String,
    val displayName: String,
    val shortDescription: String = "",
    val userPromptText: String = "",
    val resourceName: String = "",
    val riskLevel: String = "Medium",
    val requiresAndroidRuntimePermission: Boolean = false,
    val androidPermissions: List<String> = emptyList(),
    val requiresHardware: Boolean = false,
    val hardwareFeature: String? = null,
    val persistenceMode: PersistenceMode = PersistenceMode.PERSISTENT,
    val availableActions: List<String> = listOf("ALLOW_ALWAYS", "ALLOW_ONCE", "BLOCK", "CANCEL")
)

data class CapabilityPromptModel(
    val requestId: String,
    val origin: String,
    val capabilities: List<CapabilityPromptItem>,
    val resources: List<String> = emptyList(),
    val riskLevel: String = "Medium",
    val isSecure: Boolean = true,
    val isIncognito: Boolean = false,
    val reason: String = "",
    val requestSource: String = "website",
    val onDecision: (decisions: Map<String, String>) -> Unit
)
