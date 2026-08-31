package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.ParsedExtension
import kotlinx.coroutines.delay

@Composable
fun ExtensionsHubList(
    extensions: List<ParsedExtension>,
    api: ExtensionEngineApi,
    modifier: Modifier = Modifier,
    onOpenPopup: (String) -> Unit = { api.openPopup(it) },
    onOpenDetails: (String) -> Unit = { api.openDetailDialog(it) },
    onOpenSettings: (String) -> Unit = {},
    onOpenPermissions: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchingWebStore by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearchingWebStore = true
            delay(450)
            isSearchingWebStore = false
        } else {
            isSearchingWebStore = false
        }
    }

    val filteredExtensions = remember(extensions, searchQuery) {
        if (searchQuery.isBlank()) extensions
        else extensions.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search extensions...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF6366F1)) },
                trailingIcon = {
                    if (isSearchingWebStore) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF6366F1))
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Web Store Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { api.openStoreScreen() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.2f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chrome Web Store",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Browse & install thousands of extensions",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF6366F1)
                    )
                }
            }
        }

        // Installed Extensions List
        if (filteredExtensions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ExtensionOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No extensions installed" else "No matching extensions found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredExtensions, key = { it.id }) { ext ->
                ExtensionManagerCard(
                    extension = ext,
                    onToggle = { enabled -> api.setEnabled(ext.id, enabled) },
                    onOpenPopup = { onOpenPopup(ext.id) },
                    onDetails = { onOpenDetails(ext.id) },
                    onExport = {
                        api.exportExtension(ext.id) { _, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = { api.shareExtension(ext.id) },
                    onUninstall = { api.uninstallExtension(ext.id) }
                )
            }
        }
    }
}
