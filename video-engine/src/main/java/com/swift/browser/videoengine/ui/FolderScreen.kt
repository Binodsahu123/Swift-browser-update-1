package com.swift.browser.videoengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.model.MediaFolder
import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.launch

@Composable
fun FolderScreen(
    engine: VideoPlayerEngine,
    onPlayVideo: (VideoItem) -> Unit
) {
    val folders by engine.libraryManager.folders.collectAsState()
    var selectedFolder by remember { mutableStateOf<MediaFolder?>(null) }

    if (selectedFolder != null) {
        FolderContentScreen(
            folder = selectedFolder!!,
            engine = engine,
            onPlayVideo = onPlayVideo,
            onBack = { selectedFolder = null }
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(folders) { folder ->
                FolderCard(folder) {
                    selectedFolder = folder
                }
            }
        }
    }
}

@Composable
fun FolderCard(folder: MediaFolder, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF334155), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📁", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(folder.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${folder.itemCount} videos", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentScreen(
    folder: MediaFolder,
    engine: VideoPlayerEngine,
    onPlayVideo: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    val allVideos by engine.libraryManager.allVideos.collectAsState()
    val folderVideos = allVideos.filter { it.folder == folder.name }
    val scope = rememberCoroutineScope()

    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B)).statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = folder.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            lazyItems(folderVideos) { video ->
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
