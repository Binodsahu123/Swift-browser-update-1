package com.swift.browser.extensionengine.ui

import com.swift.browser.extensionengine.ExtensionMeta
import com.swift.browser.extensionengine.ParsedExtension
import com.swift.browser.extensionengine.PendingExtensionPermissionRequest

data class ExtensionUiState(
    val installedExtensions: List<ParsedExtension> = emptyList(),
    val enabledExtensions: List<ParsedExtension> = emptyList(),
    val disabledExtensions: List<ParsedExtension> = emptyList(),
    val selectedExtension: ParsedExtension? = null,
    val activePopupExtension: ParsedExtension? = null,
    val activePopupUrl: String? = null,
    val pendingPermissionRequest: PendingExtensionPermissionRequest? = null,
    val isInstalling: Boolean = false,
    val installProgressMessage: String? = null,
    val storeQuery: String = "",
    val storeResults: List<ExtensionMeta> = emptyList(),
    val isStoreLoading: Boolean = false,
    val errorMessage: String? = null,
    val showManagerOverlay: Boolean = false,
    val showActiveHubDialog: Boolean = false,
    val showPopupBottomSheet: Boolean = false,
    val showStoreScreen: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val showAnalyzerScreen: Boolean = false,
    val showDetailDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val selectedSettingsExtensionId: String? = null,
    val showDeepAnalyzerDialog: Boolean = false,
    val selectedAnalyzerExtensionId: String? = null,
    val showDeveloperConsole: Boolean = false,
    val showZipInstaller: Boolean = false,
    val customScript: String = "",
    val isCustomScriptEnabled: Boolean = false,
    val currentCwsExtensionId: String? = null
)
