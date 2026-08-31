package com.swift.browser.videoengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.library.VideoViewMode
import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllVideosScreen(
    engine: VideoPlayerEngine,
    onPlayVideo: (VideoItem) -> Unit
) {
    val videos by engine.libraryManager.allVideos.collectAsState()
    val isScanning by engine.libraryManager.isScanning.collectAsState()
    val searchQuery by engine.libraryManager.searchQuery.collectAsState()
    val viewMode by engine.libraryManager.viewMode.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }

    val filteredAndSortedVideos = remember(videos, searchQuery) {
        engine.libraryManager.getFilteredAndSortedVideos()
    }

    if (isScanning && videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.Green)
        }
        return
    }

    if (filteredAndSortedVideos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (searchQuery.isNotBlank()) "No videos matching '$searchQuery'" else "No videos found",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        return
    }

    when (viewMode) {
        VideoViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredAndSortedVideos) { video ->
                    VideoGridItem(
                        video = video,
                        onClick = {
                            engine.libraryManager.historyManager.addToHistory(video)
                            onPlayVideo(video)
                        },
                        onOptionsClick = { selectedVideoForOptions = video }
                    )
                }
            }
        }
        VideoViewMode.COMPACT_GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAndSortedVideos) { video ->
                    VideoCompactGridItem(
                        video = video,
                        onClick = {
                            engine.libraryManager.historyManager.addToHistory(video)
                            onPlayVideo(video)
                        }
                    )
                }
            }
        }
        else -> { // LIST
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredAndSortedVideos) { video ->
                    VideoListItem(
                        video = video,
                        onClick = {
                            engine.libraryManager.historyManager.addToHistory(video)
                            onPlayVideo(video)
                        },
                        onOptionsClick = {
                            selectedVideoForOptions = video
                        }
                    )
                }
            }
        }
    }

    if (selectedVideoForOptions != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedVideoForOptions = null },
            containerColor = Color(0xFF1E293B)
        ) {
            VideoOptionsContent(
                video = selectedVideoForOptions!!,
                onDismiss = { selectedVideoForOptions = null },
                onRename = { newName ->
                    engine.libraryManager.renameVideo(scope, selectedVideoForOptions!!, newName)
                    selectedVideoForOptions = null
                },
                onDelete = {
                    engine.libraryManager.deleteVideo(scope, selectedVideoForOptions!!)
                    selectedVideoForOptions = null
                },
                onExtractAudio = {
                    scope.launch { engine.extractAudio(selectedVideoForOptions!!) }
                }
            )
        }
    }
}

@Composable
fun VideoListItem(video: VideoItem, onClick: () -> Unit, onOptionsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            val context = LocalContext.current
            val imageRequest = coil.request.ImageRequest.Builder(context)
                .data(video.thumbnailUri)
                .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                .crossfade(true)
                .build()

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "▶ ${video.durationFormatted ?: "0:00"}",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val resolution = if (video.width != null && video.height != null) "${video.height}p" else "Unknown"
            Text(
                text = "$resolution | ${video.sizeFormatted}",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = "Folder", tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = video.folder,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onOptionsClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
        }
    }
}

@Composable
fun VideoGridItem(video: VideoItem, onClick: () -> Unit, onOptionsClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.DarkGray)
            ) {
                val context = LocalContext.current
                val imageRequest = coil.request.ImageRequest.Builder(context)
                    .data(video.thumbnailUri)
                    .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                    .crossfade(true)
                    .build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationFormatted ?: "0:00",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.sizeFormatted,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onOptionsClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun VideoCompactGridItem(video: VideoItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        val context = LocalContext.current
        val imageRequest = coil.request.ImageRequest.Builder(context)
            .data(video.thumbnailUri)
            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
            .crossfade(true)
            .build()

        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(4.dp)
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VideoOptionsContent(
    video: VideoItem,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onExtractAudio: () -> Unit = {}
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFileInfo by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }

    val options = listOf(
        Icons.Default.Audiotrack to "Convert to mp3",
        Icons.Default.BrandingWatermark to "Background play",
        Icons.Default.StarBorder to "Favorite",
        Icons.Default.Send to "File Transfer",
        Icons.Default.PlaylistAdd to "Add to playlist",
        Icons.Default.Lock to "Move into Privacy Folder",
        Icons.Default.Edit to "Rename",
        Icons.Default.Delete to "Delete",
        Icons.Default.CleaningServices to "Save More Space",
        Icons.Default.Info to "File info",
        Icons.Default.VolumeOff to "Mute play"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        options.forEach { (icon, title) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (title) {
                            "File info" -> showFileInfo = true
                            "Convert to mp3" -> { showConvertDialog = true; onExtractAudio() }
                            "Rename" -> showRenameDialog = true
                            "Delete" -> showDeleteDialog = true
                            else -> onDismiss()
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, color = Color.White, fontSize = 16.sp)
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(video.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Video", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Green,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(newName); showRenameDialog = false }) { Text("Rename", color = Color.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Video", color = Color.White) },
            text = { Text("Are you sure you want to delete this video?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showFileInfo) {
        AlertDialog(
            onDismissRequest = { showFileInfo = false },
            title = { Text("Information", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Title: ${video.title}", color = Color.LightGray)
                    val resolution = if (video.width != null && video.height != null) "${video.width}x${video.height}" else "Unknown"
                    Text("Resolution: $resolution", color = Color.LightGray)
                    Text("Size: ${video.sizeFormatted}", color = Color.LightGray)
                    Text("Format: video/mp4", color = Color.LightGray)
                    Text("Path: ${video.path}", color = Color.LightGray)
                    Text("Duration: ${video.durationFormatted ?: "Unknown"}", color = Color.LightGray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFileInfo = false
                    onDismiss()
                }) {
                    Text("CLOSE", color = Color.Green)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showConvertDialog) {
        var progress by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (progress < 100) {
                kotlinx.coroutines.delay(20)
                progress += 5
            }
        }

        AlertDialog(
            onDismissRequest = { /* No dismiss */ },
            title = { Text(if (progress < 100) "Saving to mp3" else "Converted successfully", color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (progress < 100) {
                        CircularProgressIndicator(progress = { progress / 100f }, color = Color.Green)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("$progress%", color = Color.White)
                    } else {
                        Text("The video was successfully converted to MP3 format.", color = Color.LightGray)
                    }
                }
            },
            confirmButton = {
                if (progress >= 100) {
                    TextButton(onClick = {
                        showConvertDialog = false
                        onDismiss()
                    }) {
                        Text("Got it", color = Color.Green)
                    }
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}
