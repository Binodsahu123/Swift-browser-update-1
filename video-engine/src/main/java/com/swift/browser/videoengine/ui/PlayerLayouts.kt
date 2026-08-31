package com.swift.browser.videoengine.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.swift.browser.videoengine.model.VideoItem
import com.swift.browser.videoengine.playback.LocalVideoEngine
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun PortraitPlayerScreen(
    video: VideoItem,
    playlist: List<VideoItem> = emptyList(),
    onVideoSelected: (VideoItem) -> Unit = {},
    videoViewProvider: () -> VideoView?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit
) {
    SharedPlayerUI(
        video = video,
        playlist = playlist,
        onVideoSelected = onVideoSelected,
        videoViewProvider = videoViewProvider,
        isPlaying = isPlaying,
        onPlayPause = onPlayPause,
        onClose = onClose,
        isLandscape = false
    )
}

@Composable
fun LandscapePlayerScreen(
    video: VideoItem,
    playlist: List<VideoItem> = emptyList(),
    onVideoSelected: (VideoItem) -> Unit = {},
    videoViewProvider: () -> VideoView?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit
) {
    SharedPlayerUI(
        video = video,
        playlist = playlist,
        onVideoSelected = onVideoSelected,
        videoViewProvider = videoViewProvider,
        isPlaying = isPlaying,
        onPlayPause = onPlayPause,
        onClose = onClose,
        isLandscape = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlayerUI(
    video: VideoItem,
    playlist: List<VideoItem>,
    onVideoSelected: (VideoItem) -> Unit,
    videoViewProvider: () -> VideoView?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    isLandscape: Boolean
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    val engine = remember { com.swift.browser.videoengine.core.VideoPlayerEngine.getInstance(context) }
    val currentPositionMs by engine.positionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var mirrorMode by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var aspectRatioMode by remember { mutableStateOf("Fit to screen") }

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var nightMode by remember { mutableStateOf(false) }

    var myVideoView by remember { mutableStateOf<VideoView?>(null) }

    // Gestures states
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showSeekIndicator by remember { mutableStateOf<String?>(null) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var currentBrightness by remember { mutableStateOf(getScreenBrightness(activity)) }

    androidx.activity.compose.BackHandler {
        if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            engine.enterPictureInPicture(activity)
        } else {
            onClose()
        }
    }

    var isInPipMode by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val act = context as? ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        act?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            act?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(isPlaying) {
        updatePipParams(context, isPlaying)
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && !isLocked && !showSettingsMenu && !showSpeedMenu) {
            delay(4000)
            showControls = false
        }
    }

    // Hide indicators
    LaunchedEffect(showVolumeIndicator, showBrightnessIndicator, showSeekIndicator) {
        if (showVolumeIndicator || showBrightnessIndicator || showSeekIndicator != null) {
            delay(1500)
            showVolumeIndicator = false
            showBrightnessIndicator = false
            showSeekIndicator = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isInPipMode) {
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        val width = size.width
                        if (offset.x < width / 3) {
                            val newPos = (engine.positionMs.value - 10000).coerceAtLeast(0L)
                            engine.seekTo(newPos)
                            showSeekIndicator = "<< 10s"
                        } else if (offset.x > width * 2 / 3) {
                            val newPos = (engine.positionMs.value + 10000).coerceAtMost(engine.durationMs.value)
                            engine.seekTo(newPos)
                            showSeekIndicator = ">> 10s"
                        } else {
                            onPlayPause()
                        }
                    },
                    onTap = {
                        if (!isLocked) {
                            showControls = !showControls
                        } else {
                            showControls = true
                        }
                    }
                )
            }
            .pointerInput(isInPipMode) {
                if (isInPipMode) return@pointerInput
                var startY = 0f
                var startX = 0f
                var startVol = 0
                var startBright = 0f
                var isHorizontalSeek = false
                var startPosMs = 0L

                detectDragGestures(
                    onDragStart = { offset ->
                        if (isLocked) return@detectDragGestures
                        startY = offset.y
                        startX = offset.x
                        startVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        startBright = getScreenBrightness(activity)
                        startPosMs = currentPositionMs
                        isHorizontalSeek = false
                    },
                    onDragEnd = {
                        if (isHorizontalSeek) {
                            showSeekIndicator = null
                        }
                    },
                    onDragCancel = {},
                    onDrag = { change, _ ->
                        if (isLocked) return@detectDragGestures
                        change.consume()
                        val deltaY = change.position.y - startY
                        val deltaX = change.position.x - startX

                        if (!isHorizontalSeek && abs(deltaX) > abs(deltaY) && abs(deltaX) > 50) {
                            isHorizontalSeek = true
                        }

                        if (isHorizontalSeek) {
                            val seekFactor = deltaX / size.width
                            val seekAmount = (seekFactor * durationMs).toLong()
                            val targetPos = (startPosMs + seekAmount).coerceIn(0L, durationMs)
                            engine.seekTo(targetPos)
                            showSeekIndicator = formatTime(targetPos.toInt())
                        } else {
                            val dragFactor = -deltaY / size.height
                            if (startX > size.width / 2) {
                                val volumeDelta = (dragFactor * maxVolume * 2).toInt()
                                val newVolume = (startVol + volumeDelta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                currentVolume = newVolume
                                showVolumeIndicator = true
                            } else {
                                val newBrightness = (startBright + dragFactor * 2).coerceIn(0f, 1f)
                                setScreenBrightness(activity, newBrightness)
                                currentBrightness = newBrightness
                                showBrightnessIndicator = true
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    myVideoView = this
                    val localEngine = LocalVideoEngine.getInstance(ctx)
                    localEngine.attachVideoView(this)
                }
            },
            onRelease = { vv ->
                LocalVideoEngine.getInstance(context).detachVideoView(vv)
            },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val mirrorScale = if (mirrorMode) -1f else 1f
                scaleX = mirrorScale * if (aspectRatioMode == "Crop") 1.5f else if (aspectRatioMode == "Stretch") 1.2f else 1f
                scaleY = if (aspectRatioMode == "Crop") 1.5f else if (aspectRatioMode == "Stretch") 1.2f else 1f
            }
        )

        // Thumbnail overlay before playback starts
        if (currentPositionMs == 0L) {
            val data = if (!video.thumbnailUri.isNullOrEmpty()) video.thumbnailUri else video.path
            val imageRequest = coil.request.ImageRequest.Builder(context)
                .data(data)
                .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                .crossfade(true)
                .build()

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
                    val mirrorScale = if (mirrorMode) -1f else 1f
                    scaleX = mirrorScale * if (aspectRatioMode == "Crop") 1.5f else if (aspectRatioMode == "Stretch") 1.2f else 1f
                    scaleY = if (aspectRatioMode == "Crop") 1.5f else if (aspectRatioMode == "Stretch") 1.2f else 1f
                }
            )
        }

        if (nightMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) {})
        }

        // Indicators
        if (showVolumeIndicator) {
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(32.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (currentVolume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(currentVolume * 100 / maxVolume)}%", color = Color.White)
                }
            }
        }
        if (showBrightnessIndicator) {
            Box(modifier = Modifier.align(Alignment.CenterStart).padding(32.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BrightnessHigh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(currentBrightness * 100).toInt()}%", color = Color.White)
                }
            }
        }
        if (showSeekIndicator != null) {
            Box(modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(24.dp)) {
                Text(showSeekIndicator!!, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                if (isLocked) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).size(48.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color.White)
                    }
                } else {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp).align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

                        Box(modifier = Modifier.clickable { Toast.makeText(context, "HDR enabled", Toast.LENGTH_SHORT).show() }.padding(horizontal = 4.dp).background(Color.Transparent, RoundedCornerShape(2.dp)).padding(2.dp)) {
                            Text("HDR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(modifier = Modifier.clickable { Toast.makeText(context, "Captions not available", Toast.LENGTH_SHORT).show() }.padding(horizontal = 4.dp).background(Color.Transparent, RoundedCornerShape(2.dp)).padding(2.dp)) {
                            Text("CC", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { showPlaylist = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "List", tint = Color.White)
                        }

                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = Color.White)
                        }
                    }

                    // Left Controls
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        IconButton(onClick = {
                            isMuted = !isMuted
                            Toast.makeText(context, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Mute", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = {
                            isLocked = true
                            showControls = false
                            Toast.makeText(context, "Screen locked", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Right Controls
                    Column(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        IconButton(onClick = {
                            myVideoView?.let { v -> takeScreenshot(v) { bitmap -> if (bitmap != null) { saveImageToGallery(context, bitmap); Toast.makeText(context, "Screenshot saved", Toast.LENGTH_SHORT).show() } else { Toast.makeText(context, "Screenshot failed", Toast.LENGTH_SHORT).show() } } }
                        }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Screenshot", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = {
                            val modes = listOf("Fit to screen", "Crop", "Stretch")
                            val nextIndex = (modes.indexOf(aspectRatioMode) + 1) % modes.size
                            aspectRatioMode = modes[nextIndex]
                            Toast.makeText(context, aspectRatioMode, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Bottom Area
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatTime(currentPositionMs.toInt()), color = Color.White, fontSize = 12.sp)
                            Slider(
                                value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                                onValueChange = {
                                    val newPos = (it * durationMs).toLong()
                                    engine.seekTo(newPos)
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.Green,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                            Text(formatTime(durationMs.toInt()), color = Color.White, fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onPlayPause,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                IconButton(onClick = {
                                    val currentIndex = playlist.indexOfFirst { it.path == video.path }
                                    if (currentIndex > 0) {
                                        onVideoSelected(playlist[currentIndex - 1])
                                    } else {
                                        Toast.makeText(context, "No previous video", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                IconButton(onClick = {
                                    val currentIndex = playlist.indexOfFirst { it.path == video.path }
                                    if (currentIndex >= 0 && currentIndex < playlist.size - 1) {
                                        onVideoSelected(playlist[currentIndex + 1])
                                    } else {
                                        Toast.makeText(context, "No next video", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                                    Text("Speed", color = Color.White, fontSize = 16.sp)
                                }
                                IconButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        try { activity?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()) } catch (e: Exception) { Toast.makeText(context, "Please enable PiP in settings", Toast.LENGTH_LONG).show() }
                                    } else {
                                        Toast.makeText(context, "PIP not supported on this device", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PIP", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                                val isLandscapeOri = LocalContext.current.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                IconButton(onClick = {
                                    if (isLandscapeOri) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    } else {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }) {
                                    Icon(if (isLandscapeOri) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Toggle Fullscreen", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed Menu Overlay
        if (showSpeedMenu) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp).background(Color.Black.copy(0.8f), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { playbackSpeed = (playbackSpeed - 0.25f).coerceAtLeast(0.25f) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                        Text("${playbackSpeed}x", color = Color.Green, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(3.0f) }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { playbackSpeed = 1.0f }) {
                        Text("Reset", color = Color.White)
                    }
                }
            }
        }

        if (showSettingsMenu) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsMenu = false },
                containerColor = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsBottomSheetContent(
                    context = context,
                    activity = activity,
                    videoPath = video.path,
                    mirrorMode = mirrorMode,
                    onToggleMirror = { mirrorMode = !mirrorMode },
                    nightMode = nightMode,
                    onToggleNightMode = { nightMode = !nightMode }
                )
            }
        }

        if (showPlaylist) {
            ModalBottomSheet(
                onDismissRequest = { showPlaylist = false },
                containerColor = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Playlist", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn {
                        items(playlist) { item ->
                            val isSelected = item.path == video.path
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onVideoSelected(item)
                                    showPlaylist = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.title,
                                    color = if (isSelected) Color.Green else Color.White,
                                    fontSize = 16.sp,
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

@Composable
fun SettingsBottomSheetContent(
    context: Context,
    activity: Activity?,
    videoPath: String,
    mirrorMode: Boolean,
    onToggleMirror: () -> Unit,
    nightMode: Boolean = false,
    onToggleNightMode: () -> Unit = {}
) {
    var brightness by remember { mutableStateOf(getScreenBrightness(activity)) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SettingsIconItem(Icons.Default.Headset, "Audio play") {
                    Toast.makeText(context, "Audio play enabled", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.Audiotrack, "Audio track") {
                    Toast.makeText(context, "Select track", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.Tune, "Equalizer") {
                    try {
                        val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                        intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "No Equalizer found", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Equalizer error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                SettingsIconItem(Icons.Default.Cast, "Cast") {
                    Toast.makeText(context, "Casting...", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SettingsIconItem(Icons.Default.Share, "Share") {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(videoPath))
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                }
                SettingsIconItem(Icons.Default.ContentCut, "Cut") {
                    Toast.makeText(context, "Cut tool", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.Star, "Favorite") {
                    Toast.makeText(context, "Added to Favorite", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.Bookmark, "Bookmark") {
                    Toast.makeText(context, "Bookmarked", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                SettingsIconItem(Icons.Default.ViewInAr, "VR") {
                    Toast.makeText(context, "VR Mode", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Play settings", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SettingsIconItem(Icons.Default.Repeat, "AB Repeat") {
                    Toast.makeText(context, "AB Repeat enabled", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.DarkMode, "Night mode", selected = nightMode) {
                    onToggleNightMode()
                    Toast.makeText(context, if (!nightMode) "Night mode ON" else "Night mode OFF", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.Flip, "Mirror mode", selected = mirrorMode) {
                    onToggleMirror()
                    Toast.makeText(context, "Mirror mode toggled", Toast.LENGTH_SHORT).show()
                }
                SettingsIconItem(Icons.Default.Timer, "Timer") {
                    Toast.makeText(context, "Timer set", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Loop", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SettingsIconItem(Icons.Default.Shuffle, "Shuffle play") {}
                SettingsIconItem(Icons.Default.RepeatOne, "Repeat current", selected = true) {}
                SettingsIconItem(Icons.Default.PlaylistPlay, "Play in order") {}
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Brightness", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BrightnessLow, contentDescription = null, tint = Color.Gray)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        setScreenBrightness(activity, it)
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Green,
                        activeTrackColor = Color.Green,
                        inactiveTrackColor = Color.Gray
                    )
                )
                Icon(Icons.Default.BrightnessHigh, contentDescription = null, tint = Color.Gray)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Decoder", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("HW Decoder", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("SW Decoder", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsIconItem(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp).width(80.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (selected) Color(0xFF2E4057) else Color.Transparent, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) Color.Green else Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = if (selected) Color.Green else Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

fun getScreenBrightness(activity: Activity?): Float {
    if (activity == null) return 0.5f
    val layoutParams = activity.window.attributes
    return if (layoutParams.screenBrightness < 0) 0.5f else layoutParams.screenBrightness
}

fun setScreenBrightness(activity: Activity?, brightness: Float) {
    if (activity == null) return
    val layoutParams = activity.window.attributes
    layoutParams.screenBrightness = brightness
    activity.window.attributes = layoutParams
}

fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun takeScreenshot(videoView: VideoView, onBitmapReady: (Bitmap?) -> Unit) {
    try {
        val bitmap = Bitmap.createBitmap(videoView.width, videoView.height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PixelCopy.request(videoView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    onBitmapReady(bitmap)
                } else {
                    onBitmapReady(null)
                }
            }, Handler(Looper.getMainLooper()))
        } else {
            onBitmapReady(null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onBitmapReady(null)
    }
}

fun saveImageToGallery(context: Context, bitmap: Bitmap): String {
    val filename = "Record_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.jpg"
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                }
            }
            return "/storage/emulated/0/Pictures/$filename"
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            val fos = FileOutputStream(image)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            return image.absolutePath
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return "Error saving"
    }
}

fun updatePipParams(context: Context, isPlaying: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = context as? ComponentActivity ?: return

        val playPauseIntent = android.app.PendingIntent.getBroadcast(
            context, 1,
            android.content.Intent("ACTION_MEDIA_PLAY_PAUSE").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = android.app.PendingIntent.getBroadcast(
            context, 2,
            android.content.Intent("ACTION_MEDIA_NEXT").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val iconPlayPause = android.graphics.drawable.Icon.createWithResource(context, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val actionPlayPause = android.app.RemoteAction(iconPlayPause, "Play/Pause", "Play or Pause", playPauseIntent)

        val iconNext = android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_next)
        val actionNext = android.app.RemoteAction(iconNext, "Next", "Next", nextIntent)

        val params = android.app.PictureInPictureParams.Builder()
            .setActions(listOf(actionPlayPause, actionNext))
            .build()

        activity.setPictureInPictureParams(params)
    }
}
