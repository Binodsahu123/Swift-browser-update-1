package com.swift.browser.permissionengine.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.permissionengine.PermissionCenterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePermissionDetailScreen(
    origin: String,
    viewModel: PermissionCenterViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val siteState by viewModel.observeSitePermissions(origin).collectAsState()

    val state = siteState ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("site_permission_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("site_detail_back_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Site Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Header Card with Origin & Connection Security
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (state.secure) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (state.secure) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.secure) "Secure connection" else "Not secure",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = "Permissions (${state.permissions.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        state.permissions.forEach { perm ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("site_perm_item_${perm.permissionType}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = perm.icon,
                            contentDescription = perm.displayName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = perm.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (perm.supportLabel != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = perm.supportLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = when (perm.riskLevel.lowercase()) {
                                    "high" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                    "medium" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                    else -> Color(0xFF10B981).copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = perm.riskLevel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (perm.riskLevel.lowercase()) {
                                        "high" -> Color(0xFFEF4444)
                                        "medium" -> Color(0xFFF59E0B)
                                        else -> Color(0xFF10B981)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (perm.androidState == "DENIED" && perm.supportLabel == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Requires Android system permission",
                                fontSize = 11.sp,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (perm.supportLabel != null) {
                        Text(
                            text = "This capability is ${perm.supportLabel.lowercase()} on this device/engine configuration.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Allow", "Session-only", "Ask", "Block").forEach { option ->
                                val isSelected = perm.decision == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (perm.canChange) {
                                            viewModel.setDecision(state.origin, perm.permissionType, option)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = option,
                                            fontSize = 11.sp
                                        )
                                    },
                                    modifier = Modifier.testTag("site_perm_chip_${perm.permissionType}_$option")
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.resetOrigin(state.origin)
                Toast.makeText(context, "Permissions reset for ${state.displayName}", Toast.LENGTH_SHORT).show()
                onBack?.invoke()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("site_perm_reset_origin_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset site permissions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
