package com.swift.browser.audioengine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.audioengine.model.PlaybackSource
import com.swift.browser.audioengine.online.OnlineMusicWebViewManager

@Composable
fun MiniOfflineAudioPlayer(
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember(context) { AudioPlayerEngine.getInstance(context) }

    val playbackSource by engine.playbackSource.collectAsState()
    val localTrack by engine.currentTrack.collectAsState()
    val isLocalPlaying by engine.isPlaying.collectAsState()
    val localPos by engine.currentPositionMs.collectAsState()
    val localDuration by engine.durationMs.collectAsState()

    val onlineTitle by OnlineMusicWebViewManager.currentTitle.collectAsState()
    val isOnlinePlaying by OnlineMusicWebViewManager.isPlaying.collectAsState()
    val onlinePos by OnlineMusicWebViewManager.currentTimeMs.collectAsState()
    val onlineDuration by OnlineMusicWebViewManager.durationMs.collectAsState()
    val hasOnlineMedia by OnlineMusicWebViewManager.hasActiveMedia.collectAsState()

    val isOnline = playbackSource == PlaybackSource.ONLINE || (playbackSource == PlaybackSource.NONE && hasOnlineMedia)
    val hasContent = (isOnline && hasOnlineMedia) || (!isOnline && localTrack != null)

    if (hasContent) {
        val title = if (isOnline) onlineTitle.ifEmpty { "Online Music" } else (localTrack?.title ?: "Playing Audio")
        val subtitle = if (isOnline) "Online Stream" else (localTrack?.artist ?: "Local Track")
        val isPlaying = if (isOnline) isOnlinePlaying else isLocalPlaying
        val pos = if (isOnline) onlinePos else localPos
        val dur = if (isOnline) onlineDuration else localDuration
        val progress = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        val themeColor = if (isOnline) Color(0xFFF43F5E) else Color(0xFF38BDF8)

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(48.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .clickable { onExpandPlayer() },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ultra-compact Icon / Art
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(themeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.Public else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            color = Color.Gray,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Compact controls
                    IconButton(
                        onClick = { engine.togglePlayPause() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { engine.next() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Tiny linear progress line
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    color = themeColor,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
