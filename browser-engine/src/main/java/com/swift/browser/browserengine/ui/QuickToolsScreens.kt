package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.BrowserViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.getValue

@Composable
fun QuickToolsOverlay(
    toolName: String,
    viewModel: BrowserViewModel,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (toolName) {
        "video" -> {
            com.swift.browser.videoengine.ui.VideoCenterHomeScreen(
                onBack = onClose
            )
        }
        "music" -> {
            com.swift.browser.audioengine.AudioCenterHomeScreen(
                onBack = onClose
            )
        }
        "passwords", "vault" -> {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onClose,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.swift.browser.passwordengine.ui.PasswordManagerScreen(
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                        onNavigateBack = onClose
                    )
                }
            }
        }
        "ai" -> {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onClose
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(550.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(
                        1.5.dp,
                        Color(0xFF818CF6).copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SWIFT AI ASSISTANT",
                                    color = Color(0xFFA5B4FC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.1f)
                        )

                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            AIChatPanel(
                                tabId = "quick_ai",
                                url = "swift://ai",
                                pageText = "",
                                onDismiss = onClose,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
        "editing" -> {
            EditingToolsScreen(
                viewModel = viewModel,
                onClose = onClose
            )
        }
        "learn" -> {
            LearnAndEarnScreen(
                onClose = onClose
            )
        }
        "permissions", "site_permissions" -> {
            com.swift.browser.permissionengine.ui.PermissionCenterScreen(
                onClose = onClose
            )
        }
        else -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = Color(0xFF0F172A)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = toolName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Coming Soon...", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAccessToolsBar(
    onToolSelect: (String) -> Unit,
    isIncognito: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioEngine = remember(context) { com.swift.browser.audioengine.api.AudioEngineApi.getInstance(context) }
    val isMusicPlaying by audioEngine.isPlaying.collectAsState(initial = false)

    Surface(
        color = if (isIncognito) Color(0xED0F172A) else Color(0xED1E293B),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickBarItem(
                label = "Video Player",
                icon = Icons.Default.PlayCircleFilled,
                color = Color(0xFF818CF8),
                onClick = { onToolSelect("video") }
            )
            QuickBarItem(
                label = "Music Player",
                icon = Icons.Default.MusicNote,
                color = Color(0xFFF43F5E),
                onClick = { onToolSelect("music") },
                showIndicator = isMusicPlaying
            )
            SwiftAIButton(
                onClick = { onToolSelect("ai") }
            )
            QuickBarItem(
                label = "Editing Tool",
                icon = Icons.Default.ContentCut,
                color = Color(0xFFA855F7),
                onClick = { onToolSelect("editing") }
            )
            QuickBarItem(
                label = "Learn & Earn",
                icon = Icons.Default.School,
                color = Color(0xFFF59E0B),
                onClick = { onToolSelect("learn") }
            )
        }
    }
}

@Composable
fun RowScope.QuickBarItem(showIndicator: Boolean = false, 
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            if (showIndicator) {
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(color = Color.Green, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width, 0f))
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwiftAIButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .shadow(16.dp, CircleShape, spotColor = Color(0xFF8B5CF6))
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899))
            ))
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Swift AI",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}


@Composable
fun SwiftAIScreen(
    query: String = "",
    onClose: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Swift AI Coming Soon", color = Color.White)
            }
        }
    }
}
