package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swift.browser.extensionengine.ExtensionEngineApi

@Composable
fun ExtensionSettingsDialog(
    show: Boolean,
    api: ExtensionEngineApi,
    extensionId: String? = null,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val uiState by api.uiState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var customScriptText by remember(uiState.customScript) { mutableStateOf(uiState.customScript) }
    var customScriptEnabled by remember(uiState.isCustomScriptEnabled) { mutableStateOf(uiState.isCustomScriptEnabled) }

    val extension = remember(extensionId, uiState.installedExtensions) {
        if (extensionId != null) uiState.installedExtensions.find { it.id == extensionId }
        else uiState.installedExtensions.firstOrNull()
    }

    val extId = extension?.id ?: extensionId ?: ""

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Extension Settings",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = extension?.name?.let { "$it Settings" } ?: "Extension Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Permissions & Policy") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Custom Script") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        val perms = extension?.permissions ?: listOf("scripting", "activeTab", "storage", "webRequest")
                        val hosts = extension?.hostPermissions ?: listOf("*://*/*")

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Profile Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = extension?.name ?: "Unknown Extension",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = extId,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (extension?.isEnabled == true) Color(0xFFE8F5E9) else Color(0xFFEEEEEE)
                                        ) {
                                            Text(
                                                text = if (extension?.isEnabled == true) "Active" else "Disabled",
                                                color = if (extension?.isEnabled == true) Color(0xFF2E7D32) else Color(0xFF616161),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Deep Diagnostic Analyzer Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (extId.isNotBlank()) {
                                                api.openDeepAnalyzerDialog(extId)
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1E1B4B)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.Code,
                                                contentDescription = null,
                                                tint = Color(0xFF818CF8),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Deep Diagnostic Analyzer",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF10B981)
                                                    ) {
                                                        Text(
                                                            "LIVE",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Inspect scripts, security traces & runtime metrics",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFC7D2FE)
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Open Analyzer",
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // API PERMISSIONS CONTROL Heading
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "API PERMISSIONS CONTROL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            items(perms) { perm ->
                                val currentDecision = api.getPermissionDecision(extId, perm)
                                val permInfo = api.getPermissionInfo(perm)
                                val hasHwPerm = permInfo?.requiredAndroidPermission?.let { api.hasAndroidPermission(it) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    permInfo?.name ?: perm,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (currentDecision) {
                                                    "ALLOW_ALWAYS" -> Color(0xFFE8F5E9)
                                                    "ALLOW_ONCE" -> Color(0xFFEEF2FF)
                                                    "BLOCK" -> Color(0xFFFFEBEE)
                                                    else -> Color(0xFFF5F5F5)
                                                }
                                            ) {
                                                Text(
                                                    text = when (currentDecision) {
                                                        "ALLOW_ALWAYS" -> "Allow Always"
                                                        "ALLOW_ONCE" -> "Allow Once"
                                                        "BLOCK" -> "Blocked"
                                                        else -> "Ask / Default"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (currentDecision) {
                                                        "ALLOW_ALWAYS" -> Color(0xFF2E7D32)
                                                        "ALLOW_ONCE" -> Color(0xFF4338CA)
                                                        "BLOCK" -> Color(0xFFC62828)
                                                        else -> Color(0xFF616161)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (permInfo?.description != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = permInfo.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (hasHwPerm != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (hasHwPerm) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (hasHwPerm) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (hasHwPerm) "Hardware permission verified on Android" else "Hardware permission missing on Android",
                                                    fontSize = 10.sp,
                                                    color = if (hasHwPerm) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val options = listOf("ALLOW_ALWAYS" to "Always", "ALLOW_ONCE" to "Once", "BLOCK" to "Block")
                                            options.forEach { (action, label) ->
                                                OutlinedButton(
                                                    onClick = {
                                                        api.setPermissionDecision(extId, perm, action)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(label, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // WEBSITE HOST ACCESS CONTROL Heading
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "WEBSITE HOST ACCESS CONTROL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            items(hosts) { host ->
                                val hostDecision = api.getHostDecision(extId, host)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Language,
                                                    contentDescription = null,
                                                    tint = Color(0xFF6366F1),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    host,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (hostDecision) {
                                                    "ALLOW_ALWAYS" -> Color(0xFFE8F5E9)
                                                    "ALLOW_ONCE" -> Color(0xFFEEF2FF)
                                                    "BLOCK" -> Color(0xFFFFEBEE)
                                                    else -> Color(0xFFF5F5F5)
                                                }
                                            ) {
                                                Text(
                                                    text = when (hostDecision) {
                                                        "ALLOW_ALWAYS" -> "Allow Always"
                                                        "ALLOW_ONCE" -> "Allow Once"
                                                        "BLOCK" -> "Blocked"
                                                        else -> "Ask / Default"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (hostDecision) {
                                                        "ALLOW_ALWAYS" -> Color(0xFF2E7D32)
                                                        "ALLOW_ONCE" -> Color(0xFF4338CA)
                                                        "BLOCK" -> Color(0xFFC62828)
                                                        else -> Color(0xFF616161)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val options = listOf("ALLOW_ALWAYS" to "Always", "ALLOW_ONCE" to "Once", "BLOCK" to "Block")
                                            options.forEach { (action, label) ->
                                                OutlinedButton(
                                                    onClick = {
                                                        api.setHostDecision(extId, host, action)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(label, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Inject User JavaScript", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Switch(
                                    checked = customScriptEnabled,
                                    onCheckedChange = {
                                        customScriptEnabled = it
                                        api.setCustomExtensionScript(customScriptText, it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customScriptText,
                                onValueChange = { customScriptText = it },
                                label = { Text("Custom User Script") },
                                placeholder = { Text("// Write JavaScript code to evaluate on page load...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    api.setCustomExtensionScript(customScriptText, customScriptEnabled)
                                    android.widget.Toast.makeText(context, "Saved Custom Extension Script", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Settings")
                            }
                        }
                    }
                }
            }
        }
    }
}

