package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.BrowserViewModel
import com.swift.browser.downloaduiengine.ActiveViewerFile
import androidx.compose.runtime.Composable

@Composable
fun FileViewerOverlay(
    activeFile: ActiveViewerFile,
    viewModel: BrowserViewModel,
    onClose: () -> Unit
) {
    LocalViewerOverlay(
        activeFile = activeFile,
        viewModel = viewModel,
        onDismiss = onClose
    )
}
