package com.swift.browser.vpnengine.presentation.ui
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swift.browser.vpnengine.data.model.VpnServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectorScreen(
    servers: List<VpnServer>,
    favorites: Set<String>,
    onServerSelected: (VpnServer) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    
    val filteredServers = remember(servers, searchQuery, favorites, filterType) {
        var result = servers
        
        when (filterType) {
            "Favorites" -> result = result.filter { favorites.contains(it.id) }
            "Fastest" -> result = result.filter { it.ping > 0 }.sortedBy { it.ping }
            "Best Quality" -> result = result.sortedByDescending { it.qualityScore }
        }
        
        if (searchQuery.isNotBlank()) {
            result = result.filter { 
                it.country.contains(searchQuery, true) || 
                it.name.contains(searchQuery, true) ||
                it.city.contains(searchQuery, true)
            }
        }
        result
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search country, hostname, or IP") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        
        ScrollableTabRow(
            selectedTabIndex = listOf("All", "Favorites", "Fastest", "Best Quality").indexOf(filterType).coerceAtLeast(0),
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("All", "Favorites", "Fastest", "Best Quality").forEach { tab ->
                Tab(
                    selected = filterType == tab,
                    onClick = { filterType = tab },
                    text = { Text(tab) }
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredServers) { server ->
                val isFav = favorites.contains(server.id)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onServerSelected(server) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.country, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text("${server.name} • ${server.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onToggleFavorite(server.id) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (server.ping > 0) "${server.ping}ms" else "-", style = MaterialTheme.typography.labelMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${server.load} sessions", style = MaterialTheme.typography.labelMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${server.speed} Mbps", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                server.tags.forEach { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                            
                            Button(
                                onClick = { onServerSelected(server) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Connect", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
