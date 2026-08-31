package com.swift.browser.downloaduiengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewRouter(
    filePath: String,
    fileName: String,
    mimeType: String,
    onBack: () -> Unit
) {
    val extension = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)
    val mime = mimeType.lowercase(Locale.ROOT)

    when {
        // 1. IMAGE PREVIEW
        mime.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> {
            ImageViewerScreen(filePath = filePath, fileName = fileName, onBack = onBack)
        }

        // 2. VIDEO PLAYER
        mime.startsWith("video/") || extension in listOf("mp4", "mkv", "webm", "avi", "3gp", "ts") -> {
            com.swift.browser.videoengine.ui.StandalonePlayerScreen(videoUrl = filePath, videoTitle = fileName, onBack = onBack)
        }

        // 3. AUDIO PLAYER
        mime.startsWith("audio/") || extension in listOf("mp3", "m4a", "wav", "aac", "ogg", "flac") -> {
            com.swift.browser.audioengine.AudioPlayerScreen(
                track = com.swift.browser.audioengine.model.AudioTrackItem(
                    id = filePath,
                    title = fileName,
                    filePath = filePath
                ),
                onBack = onBack
            )
        }

        // 4. PDF VIEWER
        mime == "application/pdf" || extension == "pdf" -> {
            PdfViewerScreen(filePath = filePath, fileName = fileName, onBack = onBack)
        }

        // 5. ZIP EXPLORER
        mime == "application/zip" || mime.contains("zip") || extension == "zip" -> {
            ArchiveExplorerScreen(filePath = filePath, fileName = fileName, onBack = onBack)
        }

        // 6. TEXT VIEWER
        mime.startsWith("text/") || extension in listOf("txt", "html", "htm", "json", "js", "css", "xml", "csv", "md", "kt", "java", "cpp", "h") -> {
            TextViewerScreen(filePath = filePath, fileName = fileName, onBack = onBack)
        }

        // 7. FALLBACK / NOT SUPPORTED CARD
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(fileName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F14))
                    )
                },
                containerColor = Color(0xFF0A0A0F)
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFFF2E2E).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF2E2E),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Preview Unavailable",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Swift currently does not support in-app preview rendering for files of type '$mimeType'. Use 'Share' or 'Open' to view via third-party application providers.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Return to Downloads", color = Color.White)
                    }
                }
            }
        }
    }
}
