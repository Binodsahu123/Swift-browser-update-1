package com.swift.browser.imageengine

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ImageViewerComponent(
    filePath: String,
    originalUrl: String,
    onDismiss: () -> Unit,
    onOpenInNewTab: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val metadata = remember(filePath, originalUrl) {
        ImageMetadataManager.resolveMetadata(context, filePath, originalUrl)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }

    var showInfoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Image with Pinch-to-zoom and double-tap gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, gestureRotation ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        rotation += gestureRotation
                        
                        val maxOffsetX = (scale - 1f) * size.width / 2f
                        val maxOffsetY = (scale - 1f) * size.height / 2f
                        offset = androidx.compose.ui.geometry.Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = androidx.compose.ui.geometry.Offset.Zero
                                rotation = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = if (filePath.startsWith("content://") || filePath.startsWith("http")) filePath else java.io.File(filePath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = rotation
                    )
            )
        }

        // Top Header Info overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = if (filePath.startsWith("content://")) "Shared Image" else java.io.File(filePath).name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Information", tint = Color.White)
            }
        }

        // Bottom Controls Overlay
        Card(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rotate Left
                ControlIconButton(icon = Icons.Default.RotateLeft, label = "Rotate L") {
                    rotation -= 90f
                }
                // Rotate Right
                ControlIconButton(icon = Icons.Default.RotateRight, label = "Rotate R") {
                    rotation += 90f
                }
                // Copy Image
                ControlIconButton(icon = Icons.Default.ContentCopy, label = "Copy") {
                    val copied = ImageCacheManager.copyImageToClipboard(context, filePath)
                    if (copied) {
                        Toast.makeText(context, "Copied image object to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to copy image to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                // Share
                ControlIconButton(icon = Icons.Default.Share, label = "Share") {
                    val intent = ImageCacheManager.getShareIntent(context, filePath)
                    if (intent != null) {
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Image"))
                    } else {
                        Toast.makeText(context, "Cannot share local image", Toast.LENGTH_SHORT).show()
                    }
                }
                // Save As
                ControlIconButton(icon = Icons.Default.Download, label = "Save As") {
                    ImageCacheManager.saveImageAs(context, filePath, if (filePath.startsWith("content://")) "Shared Image" else java.io.File(filePath).name) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
                // Open in Tab
                if (onOpenInNewTab != null) {
                    ControlIconButton(icon = Icons.Default.OpenInNew, label = "New Tab") {
                        onOpenInNewTab(originalUrl)
                    }
                }
            }
        }

        // Image Information Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Close")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF6366F1))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Image Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow(label = "Resolution", value = metadata.resolution)
                        InfoRow(label = "File Size", value = metadata.size)
                        InfoRow(label = "Format", value = metadata.format)
                        InfoRow(label = "Location", value = filePath)
                        InfoRow(label = "Original URL", value = metadata.url)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFF1E293B),
                textContentColor = Color.White,
                titleContentColor = Color.White
            )
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label.uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 13.sp, color = Color.White, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))
    }
}
