package com.swift.browser.videoengine.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.library.VideoSortBy
import com.swift.browser.videoengine.library.VideoViewMode
import com.swift.browser.videoengine.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCenterHomeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember { VideoPlayerEngine.getInstance(context) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Video", "Folder", "Playlist")

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) engine.libraryManager.scanVideos(scope)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            engine.libraryManager.scanVideos(scope)
        }
    }

    var activePlaybackVideo by remember { mutableStateOf<VideoItem?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val searchQuery by engine.libraryManager.searchQuery.collectAsState()
    val watchHistory by engine.libraryManager.historyManager.watchHistory.collectAsState()
    val viewMode by engine.libraryManager.viewMode.collectAsState()

    BackHandler(enabled = true) {
        when {
            activePlaybackVideo != null -> {
                activePlaybackVideo = null
            }
            isSearchActive -> {
                isSearchActive = false
                engine.libraryManager.searchQuery.value = ""
            }
            selectedTab != 0 -> {
                selectedTab = 0
            }
            else -> {
                onBack()
            }
        }
    }

    if (!hasPermission) {
        PermissionExplanationScreen(onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        })
        return
    }

    if (activePlaybackVideo != null) {
        PlayerScreen(
            video = activePlaybackVideo!!,
            onClose = { activePlaybackVideo = null }
        )
        return
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                engine.libraryManager.searchQuery.value = ""
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { engine.libraryManager.searchQuery.value = it },
                                placeholder = { Text("Search videos...", color = Color.Gray) },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { engine.libraryManager.searchQuery.value = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Green,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )
                        } else {
                            Text(
                                "Video Player",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }

                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(
                                        imageVector = when (viewMode) {
                                            VideoViewMode.GRID -> Icons.Default.GridView
                                            VideoViewMode.COMPACT_GRID -> Icons.Default.Apps
                                            else -> Icons.Default.ViewList
                                        },
                                        contentDescription = "Filter View Mode",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Line by line (List)", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null, tint = Color.Green) },
                                        onClick = {
                                            engine.libraryManager.setViewMode(VideoViewMode.LIST)
                                            showFilterMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("4-Corner Grid (Cards)", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, tint = Color.Green) },
                                        onClick = {
                                            engine.libraryManager.setViewMode(VideoViewMode.GRID)
                                            showFilterMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compact Tile Grid", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, tint = Color.Green) },
                                        onClick = {
                                            engine.libraryManager.setViewMode(VideoViewMode.COMPACT_GRID)
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    DropdownMenuItem(text = { Text("Sort by Date", color = Color.White) }, onClick = { engine.libraryManager.setSortBy(VideoSortBy.DATE); showSortMenu = false })
                                    DropdownMenuItem(text = { Text("Sort by Name", color = Color.White) }, onClick = { engine.libraryManager.setSortBy(VideoSortBy.NAME); showSortMenu = false })
                                    DropdownMenuItem(text = { Text("Sort by Size", color = Color.White) }, onClick = { engine.libraryManager.setSortBy(VideoSortBy.SIZE); showSortMenu = false })
                                    DropdownMenuItem(text = { Text("Sort by Length", color = Color.White) }, onClick = { engine.libraryManager.setSortBy(VideoSortBy.DURATION); showSortMenu = false })
                                }
                            }
                        }
                    }

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.Green,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        color = if (selectedTab == index) Color.Green else Color.Gray,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (watchHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("History", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = engine.libraryManager.historyManager.getWatchHistorySizeFormatted(),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        IconButton(
                            onClick = { engine.libraryManager.historyManager.clearHistory() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(watchHistory) { video ->
                        Box(
                            modifier = Modifier
                                .size(120.dp, 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                                .clickable {
                                    engine.libraryManager.historyManager.addToHistory(video)
                                    activePlaybackVideo = video
                                }
                        ) {
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
                                Text(video.durationFormatted ?: "", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> AllVideosScreen(engine, onPlayVideo = { activePlaybackVideo = it })
                    1 -> FolderScreen(engine, onPlayVideo = { activePlaybackVideo = it })
                    2 -> PlaylistScreen(engine, onPlayVideo = { activePlaybackVideo = it })
                }
            }
        }
    }
}

@Composable
fun VideoCenterScreen(onBack: () -> Unit) {
    VideoTheme {
        VideoCenterHomeScreen(onBack = onBack)
    }
}
