package com.swift.browser.webstudio.console

import androidx.compose.runtime.Composable
import com.swift.browser.developertoolsengine.DeveloperPanelComponent

@Composable
fun DeveloperConsole(htmlContent: String, onClose: () -> Unit) {
    DeveloperPanelComponent(htmlContent = htmlContent, onClose = onClose)
}
