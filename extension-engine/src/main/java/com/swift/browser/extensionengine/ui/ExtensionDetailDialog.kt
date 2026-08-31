package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.ParsedExtension

@Composable
fun ExtensionDetailDialog(
    show: Boolean,
    extension: ParsedExtension?,
    api: ExtensionEngineApi,
    onDismiss: () -> Unit,
    onConfirmInstall: (() -> Unit)? = null
) {
    if (!show || extension == null) return

    val dangerousPermissionsList = remember(extension.permissions) {
        extension.permissions.filter { perm ->
            val lower = perm.lowercase()
            lower.contains("camera") || lower.contains("microphone") ||
                    lower.contains("location") || lower.contains("webrequest") ||
                    lower.contains("cookies") || lower.contains("scripting") || lower.contains("all_urls")
        }
    }

    val normalPermissionsList = remember(extension.permissions) {
        extension.permissions.filter { perm -> !dangerousPermissionsList.contains(perm) }
    }

    val hostsList = remember(extension.hostPermissions) {
        if (extension.hostPermissions.isNotEmpty()) extension.hostPermissions
        else listOf("*://*/*")
    }

    val runsInBackground = remember(extension) {
        if (extension.backgroundScripts.isNotEmpty() || extension.backgroundPath.isNotBlank()) "Yes" else "No"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ExtensionIconMapper.getIconForExtension(extension.id, extension.name),
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Install Extension",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Review installation and requested permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Profile Section
                    item {
                        Text(
                            text = "Extension Profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Name: ${extension.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Version: ${extension.version}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Developer: ${if (extension.shortName.isNotBlank()) extension.shortName else "WebExtension Community"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Manifest Version: V${extension.manifestVersion}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Background Scripts: $runsInBackground",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Description Section
                    if (extension.description.isNotBlank()) {
                        item {
                            Text(
                                text = "Description",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = extension.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Requested API Permissions Section
                    item {
                        Text(
                            text = "Requested API Permissions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(dangerousPermissionsList) { perm ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Dangerous Permission",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = perm,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "High Impact",
                                fontSize = 10.sp,
                                color = Color(0xFFB71C1C),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(normalPermissionsList) { perm ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Normal Permission",
                                tint = Color(0xFF388E3C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = perm,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Permitted Website Hosts Section
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Permitted Website Hosts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(hostsList) { host ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEEF2FF), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Website Host",
                                tint = Color(0xFF4338CA),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = host,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF3730A3)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons: Cancel & Approve & Install
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (onConfirmInstall != null) {
                                onConfirmInstall()
                            } else {
                                api.confirmInstall(
                                    extensionId = extension.id,
                                    selectedPermissions = extension.permissions,
                                    selectedHostPermissions = extension.hostPermissions
                                )
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve & Install", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

