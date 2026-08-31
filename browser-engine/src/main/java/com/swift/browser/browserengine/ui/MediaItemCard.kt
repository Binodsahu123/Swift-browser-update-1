package com.swift.browser.browserengine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swift.browser.browserengine.LocalMediaItem
import com.swift.browser.browserengine.MediaType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemCard(
    item: LocalMediaItem,
    viewMode: ViewMode,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(item.title) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter new name for the file:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(renameInput)
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (viewMode == ViewMode.LIST) {
        // List Representation
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media Thumbnail or Placeholder
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.thumbnailUri != null) {
                        AsyncImage(
                            model = item.thumbnailUri,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val icon = when (item.type) {
                            MediaType.VIDEO -> Icons.Default.VideoLibrary
                            MediaType.AUDIO -> Icons.Default.MusicNote
                            MediaType.IMAGE -> Icons.Default.PhotoLibrary
                            MediaType.DOCUMENT -> Icons.Default.Description
                        }
                        val tint = when (item.type) {
                            MediaType.VIDEO -> Color(0xFF7C4DFF)
                            MediaType.AUDIO -> Color(0xFF00E5FF)
                            MediaType.IMAGE -> Color(0xFFFF4081)
                            MediaType.DOCUMENT -> Color(0xFFFFC107)
                        }
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                    }

                    // Duration Badge overlay
                    if (item.durationFormatted != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(item.durationFormatted, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Metadata Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge for folder
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(item.folder, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        Text(
                            text = "${item.sizeFormatted} • ${if (item.artist != null) item.artist else "Local File"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Favorite Indicator
                if (item.isFavorite) {
                    Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Options Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play / Open") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.isFavorite) "Remove Favorite" else "Add to Favorite") },
                            leadingIcon = { Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                        if (item.type == MediaType.VIDEO || item.type == MediaType.AUDIO) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onAddToPlaylist()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    } else {
        // Grid / Compact Grid / Large Grid Representation
        val cardHeight = when (viewMode) {
            ViewMode.COMPACT_GRID -> 120.dp
            ViewMode.LARGE_GRID -> 180.dp
            else -> 150.dp
        }
        
        Card(
            modifier = modifier
                .height(cardHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background thumbnail / gradient
                if (item.thumbnailUri != null) {
                    AsyncImage(
                        model = item.thumbnailUri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val gradient = when (item.type) {
                        MediaType.VIDEO -> Brush.verticalGradient(listOf(Color(0xFF673AB7), Color(0xFF512DA8)))
                        MediaType.AUDIO -> Brush.verticalGradient(listOf(Color(0xFF00BCD4), Color(0xFF0097A7)))
                        MediaType.IMAGE -> Brush.verticalGradient(listOf(Color(0xFFE91E63), Color(0xFFC2185B)))
                        MediaType.DOCUMENT -> Brush.verticalGradient(listOf(Color(0xFFFF9800), Color(0xFFF57C00)))
                    }
                    Box(modifier = Modifier.fillMaxSize().background(gradient), contentAlignment = Alignment.Center) {
                        val icon = when (item.type) {
                            MediaType.VIDEO -> Icons.Default.VideoLibrary
                            MediaType.AUDIO -> Icons.Default.MusicNote
                            MediaType.IMAGE -> Icons.Default.PhotoLibrary
                            MediaType.DOCUMENT -> Icons.Default.Description
                        }
                        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(36.dp))
                    }
                }

                // Dark Overlay on bottom for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.sizeFormatted,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                            if (item.isFavorite) {
                                Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = Color.Red, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                // Duration Badge Top-Right
                if (item.durationFormatted != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(item.durationFormatted, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Category tag Top-Left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(item.folder, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Options trigger dot
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp)
                        .size(28.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play / Open") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.isFavorite) "Remove Favorite" else "Add to Favorite") },
                        leadingIcon = { Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onToggleFavorite()
                        }
                    )
                    if (item.type == MediaType.VIDEO || item.type == MediaType.AUDIO) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
