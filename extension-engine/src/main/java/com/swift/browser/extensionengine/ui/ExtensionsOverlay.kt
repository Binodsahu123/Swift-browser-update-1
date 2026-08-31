package com.swift.browser.extensionengine.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.ParsedExtension

@Composable
fun ExtensionsOverlay(
    show: Boolean,
    api: ExtensionEngineApi,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val state by api.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()

    var activeTabIdx by remember { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            "Extensions Hub",
            "ZIP Extension Installer",
            "Developer Console"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.background,
            contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                // Exact OLD Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isDark) Color.White else LocalContentColor.current
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chrome Extensions",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // TabRow
                TabRow(
                    selectedTabIndex = activeTabIdx,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF6366F1),
                    divider = {}
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = activeTabIdx == idx,
                            onClick = { activeTabIdx = idx },
                            text = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Installation Progress Indicator
                if (state.isInstalling) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF6366F1)
                    )
                    state.installProgressMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6366F1),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // Error alert if present
                state.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { api.dismissError() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                            }
                        }
                    }
                }

                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTabIdx) {
                        0 -> ExtensionsHubList(
                            extensions = state.installedExtensions,
                            api = api
                        )
                        1 -> ZipExtensionInstallerContent(api = api)
                        2 -> DeveloperConsoleContent(api = api)
                    }
                }
            }
        }
    }
}

@Composable
fun ZipExtensionInstallerContent(api: ExtensionEngineApi) {
    val context = LocalContext.current
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            api.installFromZip(uri)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ZIP Extension Installer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select a local WebExtension archive (.zip or .crx) containing manifest.json to install.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { zipPickerLauncher.launch("application/zip") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select ZIP File")
            }
        }
    }
}

@Composable
fun DeveloperConsoleContent(api: ExtensionEngineApi) {
    var subTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Custom Scripts", "Trace Logs", "Metrics")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            subTabs.forEachIndexed { idx, label ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (subTab) {
                0 -> ScriptRunnerSubPanel(api = api)
                1 -> DiagnosticTracesSubPanel()
                2 -> ExtensionMetricsSubPanel(api = api)
            }
        }
    }
}

@Composable
fun ExtensionManagerCard(
    extension: ParsedExtension,
    onToggle: (Boolean) -> Unit,
    onOpenPopup: () -> Unit,
    onDetails: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = ExtensionIconMapper.getIconForExtension(extension.id, extension.name),
                    contentDescription = null,
                    tint = if (extension.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = extension.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "v${extension.version}",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (extension.description.isNotBlank()) {
                        Text(
                            text = extension.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = extension.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDetails) {
                    Icon(Icons.Default.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.primary)
                }
                if (extension.isEnabled && extension.actionPopup.isNotBlank()) {
                    IconButton(onClick = onOpenPopup) {
                        Icon(Icons.Default.Launch, contentDescription = "Open Popup", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.Download, contentDescription = "Export ZIP", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.tertiary)
                }
                IconButton(onClick = onUninstall) {
                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
