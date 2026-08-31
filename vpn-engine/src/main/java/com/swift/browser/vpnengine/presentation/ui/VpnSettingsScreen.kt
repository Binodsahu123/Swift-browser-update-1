package com.swift.browser.vpnengine.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swift.browser.vpnengine.domain.VpnSettings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnSettingsScreen(
    settings: VpnSettings,
    onSettingsChanged: (VpnSettings) -> Unit,
    onGenerateDiagnostics: () -> Unit = {},
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    text = "Connection",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                SettingSwitchItem(
                    title = "Auto Connect",
                    description = "Automatically connect to the best server",
                    checked = settings.autoConnect,
                    onCheckedChange = { onSettingsChanged(settings.copy(autoConnect = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Connect on untrusted Wi-Fi",
                    description = "Protect your device on public networks",
                    checked = settings.connectOnWifi,
                    onCheckedChange = { onSettingsChanged(settings.copy(connectOnWifi = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Kill Switch",
                    description = "Block internet if VPN drops",
                    checked = settings.killSwitch,
                    onCheckedChange = { onSettingsChanged(settings.copy(killSwitch = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Split Tunneling",
                    description = "Allow specific apps to bypass VPN",
                    checked = settings.splitTunneling,
                    onCheckedChange = { onSettingsChanged(settings.copy(splitTunneling = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Always On VPN",
                    description = "Keep VPN running persistently",
                    checked = settings.alwaysOn,
                    onCheckedChange = { onSettingsChanged(settings.copy(alwaysOn = it)) }
                )
            }
item {
                Text(
                    text = "Server Updates",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                var showDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Refresh Servers", style = MaterialTheme.typography.titleMedium)
                        val text = when (settings.autoRefreshInterval) {
                            0L -> "OFF (Manual Only)"
                            5 * 60000L -> "5 Minutes"
                            15 * 60000L -> "15 Minutes"
                            30 * 60000L -> "30 Minutes"
                            60 * 60000L -> "1 Hour"
                            else -> "OFF"
                        }
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Auto Refresh Servers") },
                        text = {
                            Column {
                                val options = listOf(
                                    0L to "OFF (Manual Only)",
                                    5 * 60000L to "5 Minutes",
                                    15 * 60000L to "15 Minutes",
                                    30 * 60000L to "30 Minutes",
                                    60 * 60000L to "1 Hour"
                                )
                                options.forEach { (value, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSettingsChanged(settings.copy(autoRefreshInterval = value))
                                                showDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = settings.autoRefreshInterval == value,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
            item {
                SettingSwitchItem(
                    title = "Refresh on App Open",
                    description = "Update servers when launching app",
                    checked = settings.refreshOnAppOpen,
                    onCheckedChange = { onSettingsChanged(settings.copy(refreshOnAppOpen = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Refresh on VPN Screen Open",
                    description = "Update servers when opening this screen",
                    checked = settings.refreshOnVpnScreenOpen,
                    onCheckedChange = { onSettingsChanged(settings.copy(refreshOnVpnScreenOpen = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Refresh on Wi-Fi Only",
                    description = "Save data by not refreshing on cellular",
                    checked = settings.refreshOnWifiOnly,
                    onCheckedChange = { onSettingsChanged(settings.copy(refreshOnWifiOnly = it)) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Background Refresh",
                    description = "Allow updates while app is in background",
                    checked = settings.backgroundRefresh,
                    onCheckedChange = { onSettingsChanged(settings.copy(backgroundRefresh = it)) }
                )
            }

        }
    }
}

@Composable
fun SettingSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
