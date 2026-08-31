package com.swift.browser.permissionengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.permissionengine.CapabilityPromptItem
import com.swift.browser.permissionengine.CapabilityPromptModel
import com.swift.browser.permissionengine.PermissionDialogEngine
import com.swift.browser.permissionengine.PermissionDescriptorRegistry
import com.swift.browser.permissionengine.PermissionIconResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionPromptDialog(
    modifier: Modifier = Modifier
) {
    val activeCapabilityPrompt by PermissionDialogEngine.activeCapabilityPrompt.collectAsState()
    val activePrompt by PermissionDialogEngine.activePrompt.collectAsState()

    val capabilityModel = activeCapabilityPrompt ?: if (activePrompt != null) {
        val p = activePrompt!!
        val promptItems = p.displayPermissions.map { displayName ->
            val desc = PermissionDescriptorRegistry.getDescriptor(p.permissionType)
            CapabilityPromptItem(
                capabilityId = desc?.capabilityId ?: p.permissionType,
                displayName = displayName,
                shortDescription = desc?.shortDescription ?: "",
                userPromptText = desc?.userPromptText ?: "",
                resourceName = p.permissionType,
                riskLevel = p.riskLevel,
                requiresAndroidRuntimePermission = desc?.requiresAndroidRuntimePermission ?: false,
                androidPermissions = desc?.androidPermissions ?: emptyList(),
                requiresHardware = desc?.requiresHardware ?: false,
                hardwareFeature = desc?.hardwareFeature,
                persistenceMode = desc?.persistenceMode ?: com.swift.browser.permissionengine.PersistenceMode.PERSISTENT
            )
        }
        CapabilityPromptModel(
            requestId = p.requestId,
            origin = p.origin,
            capabilities = promptItems,
            resources = p.displayPermissions,
            riskLevel = p.riskLevel,
            isSecure = p.isSecure,
            reason = "Website requested permissions",
            requestSource = "website",
            onDecision = { decisions ->
                val primaryDec = decisions.values.firstOrNull() ?: "CANCEL"
                p.onResponse(primaryDec)
            }
        )
    } else null

    if (capabilityModel == null) return

    // Track independent decisions per capability in local state
    val capabilityDecisions = remember(capabilityModel.requestId) {
        mutableStateMapOf<String, String>().apply {
            capabilityModel.capabilities.forEach { cap ->
                put(cap.capabilityId, "ALLOW_ALWAYS")
            }
        }
    }

    var isSubmitting by remember(capabilityModel.requestId) { mutableStateOf(false) }

    fun submitDecisions(decisions: Map<String, String>) {
        if (isSubmitting) return
        isSubmitting = true
        capabilityModel.onDecision(decisions)
        PermissionDialogEngine.dismissPrompt(capabilityModel.requestId)
    }

    AlertDialog(
        onDismissRequest = {
            submitDecisions(capabilityModel.capabilities.associate { it.capabilityId to "CANCEL" })
        },
        modifier = modifier.testTag("orion_permission_prompt_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Permission Request",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Origin and security context indicator
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (capabilityModel.isSecure) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (capabilityModel.isSecure) "Secure connection" else "Not secure",
                            modifier = Modifier.size(16.dp),
                            tint = if (capabilityModel.isSecure) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = capabilityModel.origin,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Incognito notice banner if applicable
                if (capabilityModel.isIncognito) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Incognito Mode: Decisions apply only until this tab closes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Dynamic prompt headline
                val promptText = formatDynamicPromptMessage(capabilityModel.origin, capabilityModel.capabilities)
                Text(
                    text = promptText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Individual capability items with independent decision selectors
                capabilityModel.capabilities.forEach { cap ->
                    val selectedDecision = capabilityDecisions[cap.capabilityId] ?: "ALLOW_ALWAYS"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("perm_item_${cap.capabilityId}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val icon = mapCapabilityToIcon(cap.capabilityId)
                                Icon(
                                    imageVector = icon,
                                    contentDescription = cap.displayName,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cap.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (cap.shortDescription.isNotBlank()) {
                                        Text(
                                            text = cap.shortDescription,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    color = when (cap.riskLevel.lowercase()) {
                                        "high" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                        "medium" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        else -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = cap.riskLevel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (cap.riskLevel.lowercase()) {
                                            "high" -> Color(0xFFEF4444)
                                            "medium" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF10B981)
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (cap.requiresAndroidRuntimePermission) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = "Requires Android system permission",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            // Independent multi-capability decision selector
                            if (capabilityModel.capabilities.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedDecision == "ALLOW_ALWAYS",
                                        onClick = { capabilityDecisions[cap.capabilityId] = "ALLOW_ALWAYS" },
                                        label = { Text("Allow", fontSize = 11.sp) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("perm_chip_allow_${cap.capabilityId}")
                                    )
                                    FilterChip(
                                        selected = selectedDecision == "ALLOW_ONCE",
                                        onClick = { capabilityDecisions[cap.capabilityId] = "ALLOW_ONCE" },
                                        label = { Text("Once", fontSize = 11.sp) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("perm_chip_once_${cap.capabilityId}")
                                    )
                                    FilterChip(
                                        selected = selectedDecision == "BLOCK",
                                        onClick = { capabilityDecisions[cap.capabilityId] = "BLOCK" },
                                        label = { Text("Block", fontSize = 11.sp) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("perm_chip_block_${cap.capabilityId}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // If multiple capabilities exist, show confirm custom selection button
                if (capabilityModel.capabilities.size > 1) {
                    Button(
                        onClick = {
                            submitDecisions(capabilityDecisions.toMap())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("perm_btn_confirm_custom")
                    ) {
                        Text("Confirm Decisions")
                    }
                }

                Button(
                    onClick = {
                        val decisions = capabilityModel.capabilities.associate { it.capabilityId to "ALLOW_ALWAYS" }
                        submitDecisions(decisions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("perm_btn_allow_always")
                ) {
                    Text(if (capabilityModel.capabilities.size > 1) "Allow all" else "Allow")
                }

                OutlinedButton(
                    onClick = {
                        val decisions = capabilityModel.capabilities.associate { it.capabilityId to "ALLOW_ONCE" }
                        submitDecisions(decisions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("perm_btn_allow_once")
                ) {
                    Text(if (capabilityModel.capabilities.size > 1) "Allow all once" else "Allow once")
                }

                TextButton(
                    onClick = {
                        val decisions = capabilityModel.capabilities.associate { it.capabilityId to "BLOCK" }
                        submitDecisions(decisions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("perm_btn_block")
                ) {
                    Text(
                        if (capabilityModel.capabilities.size > 1) "Block all" else "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                TextButton(
                    onClick = {
                        val decisions = capabilityModel.capabilities.associate { it.capabilityId to "CANCEL" }
                        submitDecisions(decisions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("perm_btn_cancel")
                ) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

fun formatDynamicPromptMessage(origin: String, capabilities: List<CapabilityPromptItem>): String {
    if (capabilities.isEmpty()) return "$origin wants to access resources on this page."

    val host = origin.removePrefix("https://").removePrefix("http://").substringBefore(":")
    if (capabilities.size == 1) {
        val item = capabilities.first()
        val desc = PermissionDescriptorRegistry.getDescriptor(item.capabilityId)
        val promptText = if (item.userPromptText.isNotBlank()) item.userPromptText else desc?.userPromptText
        return if (!promptText.isNullOrBlank()) {
            "$host $promptText"
        } else {
            "$host wants to access ${item.displayName.lowercase()}."
        }
    }

    val displayNames = capabilities.map { it.displayName.lowercase() }
    val joined = if (displayNames.size == 2) {
        "${displayNames[0]} and ${displayNames[1]}"
    } else {
        displayNames.dropLast(1).joinToString(", ") + ", and " + displayNames.last()
    }
    return "$host wants to use your $joined."
}

fun mapCapabilityToIcon(capabilityId: String): androidx.compose.ui.graphics.vector.ImageVector {
    val desc = PermissionDescriptorRegistry.getDescriptor(capabilityId)
    return PermissionIconResolver.getIcon(desc?.iconKey)
}
