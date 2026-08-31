package com.swift.browser.desktopengine.ui

import android.content.Context
import android.webkit.WebView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swift.browser.desktopengine.api.DesktopEngineProvider

@Composable
fun DesktopModeMenuItem(
    tabId: String = "",
    currentUrl: String = "",
    context: Context? = null,
    webView: WebView? = null,
    isDesktop: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = { Text("Desktop site") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.DesktopMac,
                contentDescription = "Desktop site"
            )
        },
        trailingIcon = {
            Checkbox(
                checked = isDesktop,
                onCheckedChange = null
            )
        },
        onClick = {
            val api = DesktopEngineProvider.api
            val newIsDesktop = api.toggleForSite(
                tabId = tabId,
                url = currentUrl,
                context = context,
                webView = webView
            )
            onToggle(newIsDesktop)
        },
        modifier = modifier
    )
}
