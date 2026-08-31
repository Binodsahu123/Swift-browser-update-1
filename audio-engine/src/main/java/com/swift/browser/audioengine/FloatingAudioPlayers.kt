package com.swift.browser.audioengine

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.audioengine.model.PlaybackSource
import com.swift.browser.audioengine.online.OnlineMusicWebViewManager

@Composable
fun FloatingAudioPlayer(
    onNavigateToMusic: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember(context) { AudioPlayerEngine.getInstance(context) }
    val isPlaying by engine.isPlaying.collectAsState()
    val currentTrack by engine.currentTrack.collectAsState()
    val source by engine.playbackSource.collectAsState()
    val currentPos by engine.currentPositionMs.collectAsState()
    val duration by engine.durationMs.collectAsState()

    var isDismissed by remember { mutableStateOf(false) }

    // Reset dismissal when track changes
    LaunchedEffect(currentTrack) {
        isDismissed = false
    }

    val shouldShow = currentTrack != null &&
            source == PlaybackSource.LOCAL &&
            !isDismissed

    if (shouldShow) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by if (isPlaying) {
            infiniteTransition.animateFloat(
                initialValue = 0.90f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
        } else {
            remember { mutableStateOf(1f) }
        }

        val progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 80.dp)
                    .height(48.dp)
                    .widthIn(max = 260.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clickable { onNavigateToMusic() },
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                        )

                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentTrack?.title ?: "Unknown Song",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack?.artist ?: "Unknown Artist",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { engine.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Next Button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { engine.next() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close/Dismiss Button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { isDismissed = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Bottom subtle linear progress bar
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(2.dp)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color.Transparent,
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingOnlineAudioPlayer(
    onNavigateToOnlineMusic: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember(context) { AudioPlayerEngine.getInstance(context) }
    val isOnlinePlaying by OnlineMusicWebViewManager.isPlaying.collectAsState()
    val hasMedia by OnlineMusicWebViewManager.hasActiveMedia.collectAsState()
    val title by OnlineMusicWebViewManager.currentTitle.collectAsState()
    val localTrack by engine.currentTrack.collectAsState()
    val source by engine.playbackSource.collectAsState()

    var isDismissed by remember { mutableStateOf(false) }

    // Reset dismissal when title changes
    LaunchedEffect(title) {
        isDismissed = false
    }

    val shouldShow = hasMedia &&
            !isDismissed &&
            (source != PlaybackSource.LOCAL || localTrack == null)

    if (shouldShow) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by if (isOnlinePlaying) {
            infiniteTransition.animateFloat(
                initialValue = 0.90f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
        } else {
            remember { mutableStateOf(1f) }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 80.dp)
                    .height(36.dp)
                    .widthIn(max = 240.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .clickable { onNavigateToOnlineMusic() },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                    )
                    
                    Text(
                        text = title.ifEmpty { "Online Music" },
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Small custom play/pause button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { OnlineMusicWebViewManager.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOnlinePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Small close button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isDismissed = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
