package com.swift.browser.permissionengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.permissionengine.PermissionCenterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { PermissionCenterViewModel(context.applicationContext as android.app.Application) }
    val uiState by viewModel.uiState.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedOrigin by remember { mutableStateOf<String?>(null) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Permissions?") },
            text = { Text("This will reset all custom permissions you have granted or denied to websites.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAll()
                        showClearConfirm = false
                    },
                    modifier = Modifier.testTag("perm_center_clear_all_confirm_btn")
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.testTag("perm_center_clear_all_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedOrigin != null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SitePermissionDetailScreen(
                origin = selectedOrigin!!,
                viewModel = viewModel,
                onBack = { selectedOrigin = null }
            )
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("swift_permission_center_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Permission Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("perm_center_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.sites.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("perm_center_clear_all_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                        }
                    }
                }
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Customized Site Permissions",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
                
                if (uiState.sites.isEmpty()) {
                    item {
                        Text(
                            text = "No custom site permission states registered yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(uiState.sites, key = { it.origin }) { site ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOrigin = site.origin }
                                .testTag("site_card_${site.origin}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(site.origin, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val summary = site.permissions.joinToString(", ") { "${it.displayName}: ${it.decision}" }
                                Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
