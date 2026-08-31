package com.swift.browser.videoengine.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.swift.browser.videoengine.core.VideoPlayerEngine
import com.swift.browser.videoengine.model.VideoItem

@Composable
fun PlayerScreen(
    video: VideoItem,
    playlist: List<VideoItem> = emptyList(),
    onVideoSelected: (VideoItem) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val engine = remember { VideoPlayerEngine.getInstance(context) }
    val isPlaying by engine.isPlaying.collectAsState()

    LaunchedEffect(video) {
        if (playlist.isNotEmpty()) {
            val idx = playlist.indexOfFirst { it.path == video.path }
            engine.setQueue(playlist, if (idx >= 0) idx else 0)
        } else {
            engine.play(video)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        DisposableEffect(Unit) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                    when (intent?.action) {
                        "ACTION_MEDIA_PLAY_PAUSE" -> engine.togglePlayPause()
                        "ACTION_MEDIA_NEXT" -> engine.next()
                        "ACTION_MEDIA_PREVIOUS" -> engine.previous()
                        "ACTION_MEDIA_FAST_FORWARD" -> engine.seekTo((engine.positionMs.value + 10000).coerceAtMost(engine.durationMs.value))
                        "ACTION_MEDIA_REWIND" -> engine.seekTo((engine.positionMs.value - 10000).coerceAtLeast(0L))
                    }
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                android.content.IntentFilter().apply {
                    addAction("ACTION_MEDIA_PLAY_PAUSE")
                    addAction("ACTION_MEDIA_NEXT")
                    addAction("ACTION_MEDIA_PREVIOUS")
                    addAction("ACTION_MEDIA_FAST_FORWARD")
                    addAction("ACTION_MEDIA_REWIND")
                },
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose {
                context.unregisterReceiver(receiver)
            }
        }

        if (isLandscape) {
            LandscapePlayerScreen(
                video = video,
                playlist = playlist,
                onVideoSelected = onVideoSelected,
                videoViewProvider = { null },
                isPlaying = isPlaying,
                onPlayPause = { engine.togglePlayPause() },
                onClose = onClose
            )
        } else {
            PortraitPlayerScreen(
                video = video,
                playlist = playlist,
                onVideoSelected = onVideoSelected,
                videoViewProvider = { null },
                isPlaying = isPlaying,
                onPlayPause = { engine.togglePlayPause() },
                onClose = onClose
            )
        }
    }
}

@Composable
fun StandalonePlayerScreen(
    videoUrl: String,
    videoTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember { VideoPlayerEngine.getInstance(context) }
    val allVideos by engine.libraryManager.allVideos.collectAsState()

    var currentVideo by remember {
        mutableStateOf(
            VideoItem(
                id = videoUrl,
                title = videoTitle,
                path = videoUrl,
                size = 0L,
                sizeFormatted = "",
                mimeType = "video/*",
                folder = "",
                dateAdded = System.currentTimeMillis(),
                duration = 0L,
                durationFormatted = ""
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerScreen(
            video = currentVideo,
            playlist = allVideos,
            onVideoSelected = { selected ->
                currentVideo = selected
            },
            onClose = onBack,
            modifier = Modifier.fillMaxSize()
        )
    }
}
