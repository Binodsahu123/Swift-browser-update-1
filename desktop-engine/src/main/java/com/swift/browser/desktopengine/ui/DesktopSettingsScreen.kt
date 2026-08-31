package com.swift.browser.desktopengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swift.browser.desktopengine.api.DesktopDefaultMode
import com.swift.browser.desktopengine.api.DesktopEngineProvider
import com.swift.browser.desktopengine.api.DesktopMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettingsScreen(
    onBack: () -> Unit = {}
) {
    val api = DesktopEngineProvider.api
    val settingsState by api.getSettingsState().collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Desktop Site Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Default View Mode",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Choose how websites should display by default on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        DesktopDefaultModeRow(
                            title = "Auto (Recommended)",
                            subtitle = "Adapts automatically based on screen size and performance",
                            icon = Icons.Default.Phonelink,
                            selected = settingsState.defaultMode == DesktopDefaultMode.AUTO,
                            onClick = { api.setDefaultMode(DesktopDefaultMode.AUTO) }
                        )
                        HorizontalDivider()
                        DesktopDefaultModeRow(
                            title = "Mobile Site",
                            subtitle = "Always request mobile optimized version by default",
                            icon = Icons.Default.Smartphone,
                            selected = settingsState.defaultMode == DesktopDefaultMode.MOBILE,
                            onClick = { api.setDefaultMode(DesktopDefaultMode.MOBILE) }
                        )
                        HorizontalDivider()
                        DesktopDefaultModeRow(
                            title = "Desktop Site",
                            subtitle = "Always request desktop version by default",
                            icon = Icons.Default.DesktopMac,
                            selected = settingsState.defaultMode == DesktopDefaultMode.DESKTOP,
                            onClick = { api.setDefaultMode(DesktopDefaultMode.DESKTOP) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Site Exceptions (${settingsState.siteExceptions.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (settingsState.siteExceptions.isEmpty()) {
                item {
                    Text(
                        text = "No site exceptions added yet. Toggling Desktop Site in the 3-dot menu will add site exceptions here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(settingsState.siteExceptions.toList()) { (host, mode) ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = host, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (mode == DesktopMode.DESKTOP) "Always Desktop" else "Always Mobile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { api.removeSiteException(host) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Exception")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopDefaultModeRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}
