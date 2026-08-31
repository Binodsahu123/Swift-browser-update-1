package com.swift.browser.videoengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.model.VideoItem
import com.swift.browser.videoengine.model.VideoPlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    engine: VideoPlayerEngine,
    onPlayVideo: (VideoItem) -> Unit
) {
    val playlists by engine.libraryManager.playlistManager.playlists.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create new playlist", tint = Color.White)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                PlaylistCard(
                    playlist = VideoPlaylist(id = "fav", name = "Favorite Videos", items = emptyList()),
                    isFavorite = true
                )
            }
            items(playlists) { playlist ->
                PlaylistCard(playlist = playlist)
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create new playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.Green,
                        focusedBorderColor = Color.Green
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        engine.libraryManager.playlistManager.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreateDialog = false
                    }
                }) {
                    Text("Create", color = Color.Green)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun PlaylistCard(playlist: VideoPlaylist, isFavorite: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (isFavorite) Color(0xFFEF4444) else Color(0xFF334155)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (isFavorite) "❤️" else "🎵", fontSize = 20.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${playlist.items.size} items", color = Color.Gray, fontSize = 12.sp)
        }
    }
}
