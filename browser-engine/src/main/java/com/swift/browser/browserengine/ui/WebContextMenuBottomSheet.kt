package com.swift.browser.browserengine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.swift.browser.browserengine.ContextMenuState

@Composable
fun WebContextMenuBottomSheet(
    state: ContextMenuState,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInNewTabGroup: (String) -> Unit,
    onOpenInIncognito: (String) -> Unit,
    onDownloadLink: (String) -> Unit,
    onAddToReadingList: (String, String) -> Unit = { _, _ -> }
) {
    if (!state.show) return

    val context = LocalContext.current
    var isPreviewActive by remember { mutableStateOf(false) }
    var isImagePreviewActive by remember { mutableStateOf(false) }

    val targetImageUrl = remember(state.imageUrl, state.url) {
        state.imageUrl.ifBlank { state.url }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { /* prevent propagation */ }
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                if (isPreviewActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Preview Page",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        onOpenInNewTab(state.url)
                                        onDismiss()
                                    }
                                ) {
                                    Text("Open fully")
                                }
                                IconButton(onClick = { isPreviewActive = false }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Preview"
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewClient = WebViewClient()
                                        settings.javaScriptEnabled = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        loadUrl(state.url)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else if (isImagePreviewActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Preview Image",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        onOpenInNewTab(targetImageUrl)
                                        onDismiss()
                                    }
                                ) {
                                    Text("Open fully")
                                }
                                IconButton(onClick = { isImagePreviewActive = false }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Preview"
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1F1F1F)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = targetImageUrl,
                                contentDescription = "Image preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.isImage) "Image Options" else "Link Options",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            // Preview button affordance
                            TextButton(
                                onClick = {
                                    if (state.isImage) {
                                        isImagePreviewActive = true
                                    } else {
                                        isPreviewActive = true
                                    }
                                }
                            ) {
                                Text(
                                    text = if (state.isImage) "Preview Image" else "Preview Page",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = if (state.isImage) targetImageUrl else state.url,
                            maxLines = 2,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        if (!state.isImage && !state.isImageLink) {
                            // =========================================
                            // NORMAL LINK ACTIONS
                            // =========================================
                            ContextMenuItem(
                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                                text = "Open in new tab",
                                onClick = {
                                    onOpenInNewTab(state.url)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.GroupWork,
                                text = "Open in new tab in group",
                                onClick = {
                                    onOpenInNewTabGroup(state.url)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.VisibilityOff,
                                text = "Open in Incognito tab",
                                onClick = {
                                    onOpenInIncognito(state.url)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.ContentCopy,
                                text = "Copy link address",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Copied", state.url)
                                    )
                                    Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Download,
                                text = "Download link",
                                onClick = {
                                    onDownloadLink(state.url)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Bookmark,
                                text = "Add to reading list",
                                onClick = {
                                    onAddToReadingList(state.url, state.url)
                                    Toast.makeText(context, "Added to reading list", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Share,
                                text = "Share link",
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, state.url)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share")
                                    )
                                    onDismiss()
                                }
                            )
                        } else if (state.isImageLink) {
                            // =========================================
                            // IMAGE + LINK ACTIONS
                            // =========================================
                            ContextMenuItem(
                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                                text = "Open link in new tab",
                                onClick = {
                                    onOpenInNewTab(state.url)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.VisibilityOff,
                                text = "Open link in Incognito tab",
                                onClick = {
                                    onOpenInIncognito(state.url)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.ContentCopy,
                                text = "Copy link address",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Copied", state.url)
                                    )
                                    Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Image,
                                text = "Open image in new tab",
                                onClick = {
                                    onOpenInNewTab(targetImageUrl)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Download,
                                text = "Download image",
                                onClick = {
                                    onDownloadLink(targetImageUrl)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Share,
                                text = "Share image",
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, targetImageUrl)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share")
                                    )
                                    onDismiss()
                                }
                            )
                        } else {
                            // =========================================
                            // IMAGE ONLY ACTIONS
                            // =========================================
                            ContextMenuItem(
                                icon = Icons.Default.Image,
                                text = "Open image in new tab",
                                onClick = {
                                    onOpenInNewTab(targetImageUrl)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.VisibilityOff,
                                text = "Open image in Incognito tab",
                                onClick = {
                                    onOpenInIncognito(targetImageUrl)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.ContentCopy,
                                text = "Copy image url",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Copied", targetImageUrl)
                                    )
                                    Toast.makeText(context, "Image URL copied!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Download,
                                text = "Download image",
                                onClick = {
                                    onDownloadLink(targetImageUrl)
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Default.Share,
                                text = "Share image",
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, targetImageUrl)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share")
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}
