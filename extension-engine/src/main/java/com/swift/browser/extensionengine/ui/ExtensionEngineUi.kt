package com.swift.browser.extensionengine.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.swift.browser.extensionengine.ExtensionEngineApi

@Composable
fun ExtensionEngineUi(
    api: ExtensionEngineApi
) {
    val state by api.uiState.collectAsState()

    // 1. Full Extensions Overlay Manager
    ExtensionsOverlay(
        show = state.showManagerOverlay,
        api = api,
        onDismiss = { api.closeManagerUi() }
    )

    // 2. Active Extension Hub (Puzzle Menu)
    ActiveExtensionsDialog(
        show = state.showActiveHubDialog,
        api = api,
        onDismiss = { api.closeActiveHub() }
    )

    // 3. Extension Action Popup Bottom Sheet
    ExtensionPopupBottomSheet(
        show = state.showPopupBottomSheet,
        extension = state.activePopupExtension,
        popupUrl = state.activePopupUrl,
        api = api,
        onDismiss = { api.closePopup() }
    )

    // 4. Extension Permission Prompt Dialog
    ExtensionPermissionDialog(
        show = state.showPermissionDialog,
        request = state.pendingPermissionRequest,
        api = api,
        onDismiss = {
            state.pendingPermissionRequest?.onResult("BLOCK")
        }
    )

    // 5. Chrome Web Store Catalog Screen
    ExtensionStoreScreen(
        show = state.showStoreScreen,
        api = api,
        onDismiss = { api.closeStoreScreen() }
    )

    // 6. Extension Deep Analyzer Screen
    ExtensionAnalyzerScreen(
        show = state.showAnalyzerScreen,
        api = api,
        onDismiss = { api.closeAnalyzerScreen() }
    )

    // 7. Extension Detail & Permission Review Dialog
    ExtensionDetailDialog(
        show = state.showDetailDialog,
        extension = state.selectedExtension,
        api = api,
        onDismiss = { api.closeDetailDialog() }
    )

    // 8. Extension Settings Dialog
    ExtensionSettingsDialog(
        show = state.showSettingsDialog,
        api = api,
        extensionId = state.selectedSettingsExtensionId,
        onDismiss = { api.closeSettingsDialog() }
    )

    // 9. Deep Extension Audit Dialog
    state.selectedAnalyzerExtensionId?.let { targetId ->
        DeepExtensionAnalyzerDialog(
            show = state.showDeepAnalyzerDialog,
            extensionId = targetId,
            api = api,
            onDismiss = { api.closeDeepAnalyzerDialog() }
        )
    }

    // 10. Developer Console Panel
    DeveloperConsolePanel(
        show = state.showDeveloperConsole,
        api = api,
        onDismiss = { api.closeDeveloperConsole() }
    )

    // 11. Zip Extension Installer
    ZipExtensionInstaller(
        show = state.showZipInstaller,
        api = api,
        onDismiss = { api.closeZipInstaller() }
    )
}
