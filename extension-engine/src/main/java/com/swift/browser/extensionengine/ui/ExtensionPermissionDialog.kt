package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.PendingExtensionPermissionRequest

@Composable
fun ExtensionPermissionDialog(
    show: Boolean,
    request: PendingExtensionPermissionRequest?,
    api: ExtensionEngineApi? = null,
    onDismiss: () -> Unit
) {
    if (!show || request == null) return

    val permInfo = api?.getPermissionInfo(request.permission)
    val permTitle = permInfo?.name ?: request.permission
    val permDesc = permInfo?.description ?: "Extension requested access to '${request.permission}'."
    val riskLevel = permInfo?.riskLevel ?: "Medium"

    val (riskColor, riskBgColor) = when (riskLevel) {
        "High" -> Color(0xFFD32F2F) to Color(0xFFFFEBEE)
        "Medium" -> Color(0xFFED6C02) to Color(0xFFFFF4E5)
        else -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Extension header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ExtensionIconMapper.getIconForExtension(request.extId, request.extName),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = request.extName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Permission Request",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Permission detail box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = permTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = riskBgColor
                            ) {
                                Text(
                                    text = "$riskLevel Risk",
                                    color = riskColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = permDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            request.onResult("ALLOW_ALWAYS")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow Always")
                    }

                    OutlinedButton(
                        onClick = {
                            request.onResult("ALLOW_ONCE")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow This Session Only")
                    }

                    TextButton(
                        onClick = {
                            request.onResult("BLOCK")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Block Permission")
                    }
                }
            }
        }
    }
}

