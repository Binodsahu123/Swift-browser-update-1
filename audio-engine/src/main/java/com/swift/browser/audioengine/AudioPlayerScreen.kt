package com.swift.browser.audioengine

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.audioengine.model.AudioPlaylist
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.PlaybackSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    track: AudioTrackItem? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember(context) { AudioPlayerEngine.getInstance(context) }

    val playbackSource by engine.playbackSource.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val isOnlinePlaying by engine.isOnlinePlaying.collectAsState()
    val currentPos by engine.currentPositionMs.collectAsState()
    val duration by engine.durationMs.collectAsState()
    val queueState by engine.queueState.collectAsState()
    val playlists by engine.playlists.collectAsState()

    val localTrack = track ?: engine.currentTrack.collectAsState().value
    val onlineTitle by engine.onlineTitle.collectAsState()
    val onlineUrl by engine.onlineUrl.collectAsState()
    val isOnlineFavorite by engine.isOnlineFavorite.collectAsState()

    val isOnline = playbackSource == PlaybackSource.ONLINE

    val title = if (isOnline) onlineTitle.ifEmpty { "Online Music" } else (localTrack?.title ?: "No Track Selected")
    val artist = if (isOnline) "Online Stream" else (localTrack?.artist ?: "Unknown Artist")
    val album = if (isOnline) "Online Music" else (localTrack?.album ?: "Local Library")
    val isFavorite = if (isOnline) isOnlineFavorite else (localTrack?.isFavorite == true)
    val activePlaying = if (isOnline) isOnlinePlaying else isPlaying

    var showQueueSheet by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var timerMinutesInput by remember { mutableStateOf("") }
    val sleepTimerMinutesLeft by engine.sleepTimerMinutesLeft.collectAsState()
    val activeSleepTimer = if (sleepTimerMinutesLeft > 0) "$sleepTimerMinutesLeft mins" else null

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 1500 },
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = Color(0xFF1E293B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Playback Queue (${queueState.tracks.size})",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (queueState.tracks.isEmpty()) {
                    Text("Queue is empty", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queueState.tracks) { idx, item ->
                            val isCurrent = idx == queueState.currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) Color(0xFF334155) else Color.Transparent)
                                    .clickable {
                                        engine.playTrack(item, queueState.tracks)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isCurrent) Color(0xFF38BDF8) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isCurrent) Color(0xFF38BDF8) else Color.White,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist ?: "Unknown",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistDialog && localTrack != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to Playlist", fontWeight = FontWeight.Bold) },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists found. Create one in Music Center first.")
                } else {
                    Column {
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    engine.addTrackToPlaylist(pl.id, localTrack)
                                    showPlaylistDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(pl.name, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRenameDialog && localTrack != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Song", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            engine.renameAudio(localTrack, renameInput)
                            showRenameDialog = false
                        }
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

    if (showDeleteDialog && localTrack != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Track", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${localTrack.title}' from your device?") },
            confirmButton = {
                Button(
                    onClick = {
                        engine.deleteAudio(localTrack)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter minutes after which playback will stop:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = timerMinutesInput,
                        onValueChange = { timerMinutesInput = it },
                        placeholder = { Text("e.g. 15, 30, 60") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = timerMinutesInput.toIntOrNull()
                        if (mins != null && mins > 0) {
                            engine.startSleepTimer(mins)
                            showTimerDialog = false
                            timerMinutesInput = ""
                        }
                    }
                ) {
                    Text("Set Timer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        engine.cancelSleepTimer()
                        showTimerDialog = false
                    }
                ) {
                    Text(if (activeSleepTimer != null) "Turn Off" else "Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Now Playing", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isOnline) "ONLINE STREAM" else "OFFLINE MUSIC",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isOnline) {
                                engine.toggleOnlineFavorite()
                            } else if (localTrack != null) {
                                engine.toggleFavorite(localTrack)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFF43F5E) else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, if (isOnline) "Listening to $title on Swift Music: $onlineUrl" else "Listening to $title by $artist")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }

                    var showMoreMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sleep Timer ${if (activeSleepTimer != null) "($activeSleepTimer)" else ""}") },
                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showTimerDialog = true
                            }
                        )
                        if (!isOnline && localTrack != null) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showPlaylistDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    renameInput = localTrack.title
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Track") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0F172A),
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visualizer / Album Artwork Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val midY = size.height / 2f
                        val width = size.width
                        val barsCount = 36
                        val barWidth = 5.dp.toPx()
                        val spacing = 4.dp.toPx()
                        val totalSpacing = (barsCount - 1) * spacing
                        val startX = (width - (barsCount * barWidth + totalSpacing)) / 2f

                        for (i in 0 until barsCount) {
                            val x = startX + i * (barWidth + spacing)
                            val multiplier = if (activePlaying) {
                                Math.sin((i.toDouble() / 3.5) + waveOffset).toFloat().coerceIn(0.12f, 1.0f)
                            } else {
                                0.15f
                            }
                            val barHeight = (size.height * 0.45f) * multiplier
                            drawRoundRect(
                                color = if (isOnline) Color(0xFFF43F5E).copy(alpha = if (activePlaying) 0.85f else 0.4f) else Color(0xFF38BDF8).copy(alpha = if (activePlaying) 0.85f else 0.4f),
                                topLeft = Offset(x, midY - barHeight / 2f),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isOnline) Icons.Default.Public else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Metadata text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$artist • $album",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Seek Bar Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentPos.toFloat(),
                    onValueChange = { pos ->
                        engine.seekTo(pos.toInt())
                    },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isOnline) Color(0xFFF43F5E) else Color(0xFF38BDF8),
                        activeTrackColor = if (isOnline) Color(0xFFF43F5E) else Color(0xFF38BDF8),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPos), color = Color.Gray, fontSize = 12.sp)
                    Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
                }
            }

            // Playback Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { engine.toggleShuffle() },
                    enabled = !isOnline
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (!isOnline && queueState.isShuffle) Color(0xFF38BDF8) else Color.Gray
                    )
                }

                IconButton(
                    onClick = { engine.previous() },
                    modifier = Modifier.background(Color(0xFF1E293B), CircleShape)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }

                IconButton(
                    onClick = { engine.togglePlayPause() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(if (isOnline) Color(0xFFF43F5E) else Color(0xFF38BDF8), CircleShape)
                ) {
                    Icon(
                        imageVector = if (activePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { engine.next() },
                    modifier = Modifier.background(Color(0xFF1E293B), CircleShape)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                }

                IconButton(
                    onClick = { engine.toggleRepeat() },
                    enabled = !isOnline
                ) {
                    Icon(
                        imageVector = if (!isOnline && queueState.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (!isOnline && queueState.repeatMode > 0) Color(0xFF38BDF8) else Color.Gray
                    )
                }
            }

            // Bottom Actions (Queue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isOnline) {
                    TextButton(
                        onClick = { showQueueSheet = true }
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Queue (${queueState.tracks.size})", color = Color(0xFF38BDF8), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val secTotal = ms / 1000
    val min = secTotal / 60
    val sec = secTotal % 60
    return String.format("%02d:%02d", min, sec)
}
