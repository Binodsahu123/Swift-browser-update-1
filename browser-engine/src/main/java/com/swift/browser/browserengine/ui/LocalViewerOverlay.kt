package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.BrowserViewModel
import com.swift.browser.browserengine.PdfUtility
import com.swift.browser.downloaduiengine.ActiveViewerFile
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Locale
import java.io.File
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import com.swift.browser.downloadengine.DownloadScheduler

@Composable
fun LocalViewerOverlay(
    activeFile: ActiveViewerFile,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)) // Slate dark aesthetic
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val icon = when {
                            activeFile.mimeType.startsWith("video/") -> Icons.Default.Movie
                            activeFile.mimeType.startsWith("audio/") -> Icons.Default.MusicNote
                            activeFile.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                            else -> Icons.Default.InsertDriveFile
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activeFile.fileName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeFile.mimeType.uppercase(),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val lowerName = activeFile.fileName.lowercase()
                    val lowerMime = activeFile.mimeType.lowercase()
                    val isTextOrCode = lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                            lowerName.endsWith(".xml") || lowerName.endsWith(".json") ||
                            lowerName.endsWith(".txt") || lowerName.endsWith(".html") ||
                            lowerName.endsWith(".css") || lowerName.endsWith(".js") ||
                            lowerName.endsWith(".md") || lowerName.endsWith(".properties") ||
                            lowerName.endsWith(".gradle") || lowerName.endsWith(".kts") ||
                            lowerMime.startsWith("text/") || lowerMime.contains("javascript") ||
                            lowerMime.contains("json") || lowerMime.contains("xml")

                    val context = LocalContext.current
                    if (isTextOrCode) {
                        IconButton(
                            onClick = {
                                try {
                                    val content = File(activeFile.filePath).readText(Charsets.UTF_8)
                                    PdfUtility.printTextToPdf(context, activeFile.fileName, content)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot read file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Save to PDF",
                                tint = Color(0xFFF43F5E)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Viewer",
                            tint = Color.White
                        )
                    }
                }

                // Viewer Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val lowerName = activeFile.fileName.lowercase()
                    val lowerMime = activeFile.mimeType.lowercase()
                    val isTextOrCode = lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                            lowerName.endsWith(".xml") || lowerName.endsWith(".json") ||
                            lowerName.endsWith(".txt") || lowerName.endsWith(".html") ||
                            lowerName.endsWith(".css") || lowerName.endsWith(".js") ||
                            lowerName.endsWith(".md") || lowerName.endsWith(".properties") ||
                            lowerName.endsWith(".gradle") || lowerName.endsWith(".kts") ||
                            lowerMime.startsWith("text/") || lowerMime.contains("javascript") ||
                            lowerMime.contains("json") || lowerMime.contains("xml")

                    val isArchive = lowerName.endsWith(".zip") || lowerName.endsWith(".apk") ||
                            lowerName.endsWith(".aab") || lowerName.endsWith(".jar") ||
                            lowerName.endsWith(".rar") || lowerName.endsWith(".7z") ||
                            lowerName.endsWith(".tar") || lowerName.endsWith(".gz") ||
                            lowerMime.contains("zip") || lowerMime.contains("compressed") ||
                            lowerMime.contains("archive")

                    when {
                        activeFile.mimeType.startsWith("video/") -> {
                            com.swift.browser.videoengine.ui.StandalonePlayerScreen(
                                videoUrl = activeFile.filePath,
                                videoTitle = activeFile.fileName,
                                onBack = onDismiss
                            )
                        }
                        activeFile.mimeType.startsWith("audio/") -> {
                            AudioHostView(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                onClose = onDismiss
                            )
                        }
                        activeFile.mimeType.contains("pdf") -> {
                            PdfRendererView(filePath = activeFile.filePath)
                        }
                        activeFile.mimeType.startsWith("image/") || isImageFile(activeFile.fileName) -> {
                            ImageOfflineViewer(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                mimeType = activeFile.mimeType
                            )
                        }
                        isArchive -> {
                            ZipExplorerView(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                onClose = onDismiss
                            )
                        }
                        isTextOrCode -> {
                            TextCodeRendererView(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                mimeType = activeFile.mimeType
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Unsupported offline file type:\n${activeFile.fileName}",
                                    textAlign = TextAlign.Center,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
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
fun AudioHostView(
    filePath: String,
    fileName: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val audioEngine = remember { com.swift.browser.audioengine.api.AudioEngineApi.getInstance(context) }

    LaunchedEffect(filePath) {
        audioEngine.playSingleTrack(filePath, fileName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color(0xFF818CF8).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = fileName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Now playing via Swift Audio Engine",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(context, Class.forName("com.swift.browser.audioengine.AudioPlayerActivity"))
                            context.startActivity(intent)
                            onClose()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Cannot open Audio Center", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Open Audio Center",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfRendererView(filePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmapList = remember(filePath) {
        val bitmaps = mutableStateListOf<Bitmap>()
        try {
            val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
                android.net.Uri.parse(filePath)
            } else {
                android.net.Uri.fromFile(File(filePath))
            }
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val scale = 2.0f
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bitmaps
    }

    if (bitmapList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF818CF8))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            itemsIndexed(bitmapList) { idx, bitmap ->
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${idx + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

// Format duration helper
private fun formatDuration(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun ImageOfflineViewer(
    filePath: String,
    fileName: String,
    mimeType: String
) {
    val context = LocalContext.current
    val bitmap = remember(filePath) {
        try {
            android.graphics.BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var rotationState by remember { mutableFloatStateOf(0f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val file = remember(filePath) { File(filePath) }
    val info = remember(filePath) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(filePath, options)
        val resolution = if (options.outWidth > 0 && options.outHeight > 0) "${options.outWidth} x ${options.outHeight}" else "Unknown"
        val format = options.outMimeType ?: mimeType
        val sizeText = formatFileSize(file.length())
        ImageInfo(resolution, sizeText, format)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Unable to load image", color = Color.White)
            }
        } else {
            // Main zooming image display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .wrapContentSize(Alignment.Center)
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = fileName,
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotationState
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            // Quick Actions Overlay (Bottom bar)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { rotationState = (rotationState + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate Clockwise", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Image Details", tint = Color.White)
                    }
                    IconButton(onClick = {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Image"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Sharing failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Image Path", file.absolutePath)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied file path to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = Color.White)
                    }
                }
            }
        }

        // Info popup
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                confirmButton = {
                    Button(onClick = { showInfoDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Image Information") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File Name: $fileName", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Resolution: ${info.resolution}", color = MaterialTheme.colorScheme.onSurface)
                        Text("File Size: ${info.size}", color = MaterialTheme.colorScheme.onSurface)
                        Text("Format: ${info.format}", color = MaterialTheme.colorScheme.onSurface)
                        Text("Stored At: ${file.parent}", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    }
}

data class ImageInfo(val resolution: String, val size: String, val format: String)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIdx = 0
    while (value >= 1024 && unitIdx < units.size - 1) {
        value /= 1024
        unitIdx++
    }
    return String.format("%.2f %s", value, units[unitIdx])
}

private fun isImageFile(fileName: String): Boolean {
    val extensions = listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico", "avif", "heic", "heif", "tiff")
    val ext = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)
    return extensions.contains(ext)
}

@Composable
fun TextCodeRendererView(filePath: String, fileName: String, mimeType: String) {
    val fileContent = remember(filePath) {
        try {
            File(filePath).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            "Failed to read file contents: ${e.message}"
        }
    }

    val extension = remember(fileName) { fileName.substringAfterLast(".", "") }
    val annotatedLines = remember(fileContent, extension) {
        fileContent.split("\n").map { line ->
            SyntaxHighlightingEngine.highlight(line, extension)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B0F19), // Dark developer canvas
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    itemsIndexed(annotatedLines) { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(4, ' ') + "  ",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.width(38.dp)
                            )
                            Text(
                                text = line,
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

object SyntaxHighlightingEngine {
    // Colors for syntax styling: IDM style / Dracula dark theme style
    val KeywordColor = Color(0xFFFF79C6) // Pink
    val TypeColor = Color(0xFF8BE9FD)    // Cyan
    val StringColor = Color(0xFFF1FA8C)  // Yellow
    val CommentColor = Color(0xFF6272A4) // Gray-blue
    val NumberColor = Color(0xFFBD93F9)  // Purple
    val DefaultColor = Color(0xFFF8F8F2) // Whiteish
    val TagColor = Color(0xFFFF5555)     // Red (HTML/XML tags)
    val AttrColor = Color(0xFF50FA7B)    // Green (HTML/XML attributes)

    val Keywords = setOf(
        // Java & Kotlin & C & C++
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "val", "var", "fun", "null", "true", "false", "object", "companion",
        "sealed", "data", "override", "open", "internal", "as", "is", "in", "when", "by", "init",
        "constructor", "as?", "in!", "out", "let", "run", "also", "apply", "takeIf", "takeUnless",
        // Python
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
        "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
        // JS & TS
        "let", "const", "var", "function", "class", "export", "import", "default", "from", "as",
        "if", "else", "switch", "case", "default", "for", "while", "do", "break", "continue",
        "return", "try", "catch", "finally", "throw", "new", "this", "typeof", "instanceof", "in",
        "of", "delete", "void", "debugger", "await", "async", "yield", "super", "extends", "static",
        "get", "set", "constructor", "arguments", "eval", "interface", "type", "namespace", "module",
        "declare", "keyof", "readonly", "any", "unknown", "never", "string", "number", "boolean",
        // C#
        "using", "namespace", "struct", "delegate", "event", "readonly", "volatile", "virtual",
        "override", "sealed", "abstract", "partial", "ref", "out", "in", "params", "lock", "async",
        "await", "foreach", "goto", "checked", "unchecked", "unsafe", "fixed", "as", "is", "sizeof",
        "typeof", "stackalloc",
        // PHP
        "echo", "print", "global", "static", "function", "class", "public", "private", "protected",
        "return", "if", "else", "elseif", "while", "do", "for", "foreach", "break", "continue", "switch",
        "case", "default", "include", "require", "include_once", "require_once", "use", "namespace",
        // Go
        "package", "import", "func", "var", "const", "struct", "interface", "map", "chan", "go",
        "select", "defer", "if", "else", "switch", "case", "default", "for", "range", "break",
        "continue", "return", "fallthrough", "type",
        // Rust
        "pub", "fn", "let", "mut", "use", "mod", "struct", "enum", "impl", "trait", "match", "if",
        "else", "loop", "while", "for", "in", "break", "continue", "return", "unsafe", "const",
        "static", "type", "as", "where", "self", "Self", "dyn", "ref",
        // Swift
        "let", "var", "func", "class", "struct", "enum", "protocol", "extension", "init", "deinit",
        "self", "Self", "import", "public", "private", "fileprivate", "internal", "open", "guard",
        "if", "else", "switch", "case", "default", "for", "in", "while", "repeat", "break", "continue",
        "fallthrough", "return", "throw", "throws", "try", "try!", "try?", "nil", "true", "false"
    )

    val Types = setOf(
        "String", "Int", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Char", "Unit", "Any",
        "List", "Map", "Set", "ArrayList", "HashMap", "HashSet", "Array", "Exception", "Runnable",
        "Thread", "Integer", "Character", "Object", "System", "Console", "Task", "Action", "Func",
        "Vector", "slice", "str", "bool", "int", "float", "dict", "tuple", "list", "set", "object",
        "nil", "None", "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128",
        "usize", "f32", "f64", "char", "bool", "Option", "Result", "Some", "None", "Ok", "Err", "Box",
        "Rc", "Arc", "RefCell", "Cell", "String", "str"
    )

    fun highlight(line: String, extension: String): androidx.compose.ui.text.AnnotatedString {
        val ext = extension.lowercase(Locale.ROOT)
        return when {
            ext == "xml" || ext == "html" || ext == "svg" -> highlightXmlHtml(line)
            ext == "json" -> highlightJson(line)
            ext == "yaml" || ext == "yml" -> highlightYaml(line)
            ext == "md" || ext == "markdown" -> highlightMarkdown(line)
            else -> highlightGeneralCode(line)
        }
    }

    private fun highlightGeneralCode(line: String): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        
        // Let's check for single line comment first
        val commentIndex = line.indexOf("//")
        val hashCommentIndex = if (line.trim().startsWith("#") && !line.trim().startsWith("#include")) 0 else -1
        val finalCommentIdx = when {
            commentIndex != -1 && hashCommentIndex != -1 -> minOf(commentIndex, hashCommentIndex)
            commentIndex != -1 -> commentIndex
            hashCommentIndex != -1 -> hashCommentIndex
            else -> -1
        }

        val codePart = if (finalCommentIdx != -1) line.substring(0, finalCommentIdx) else line
        val commentPart = if (finalCommentIdx != -1) line.substring(finalCommentIdx) else ""

        // Simple tokenizer for codePart
        var index = 0
        var insideString = false
        var stringChar = ' '
        var currentWord = StringBuilder()

        fun flushWord() {
            if (currentWord.isNotEmpty()) {
                val word = currentWord.toString()
                when {
                    Keywords.contains(word) -> {
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                        builder.append(word)
                        builder.pop()
                    }
                    Types.contains(word) -> {
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TypeColor))
                        builder.append(word)
                        builder.pop()
                    }
                    word.all { it.isDigit() } -> {
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = NumberColor))
                        builder.append(word)
                        builder.pop()
                    }
                    else -> {
                        builder.append(word)
                    }
                }
                currentWord = StringBuilder()
            }
        }

        while (index < codePart.length) {
            val char = codePart[index]
            if (insideString) {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                builder.append(char)
                builder.pop()
                if (char == stringChar && (index == 0 || codePart[index - 1] != '\\')) {
                    insideString = false
                }
                index++
            } else {
                if (char == '"' || char == '\'') {
                    flushWord()
                    insideString = true
                    stringChar = char
                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                    builder.append(char)
                    builder.pop()
                    index++
                } else if (char.isLetterOrDigit() || char == '_') {
                    currentWord.append(char)
                    index++
                } else {
                    flushWord()
                    builder.append(char)
                    index++
                }
            }
        }
        flushWord()

        // Append comment if exists
        if (commentPart.isNotEmpty()) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = CommentColor))
            builder.append(commentPart)
            builder.pop()
        }

        return builder.toAnnotatedString()
    }

    private fun highlightXmlHtml(line: String): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        var index = 0
        var insideTag = false
        var insideString = false
        var stringChar = ' '
        var currentToken = StringBuilder()

        fun flushToken() {
            if (currentToken.isNotEmpty()) {
                val tok = currentToken.toString()
                if (insideTag) {
                    when {
                        tok.startsWith("<") || tok.startsWith("/") || tok.endsWith(">") -> {
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TagColor, fontWeight = FontWeight.Bold))
                            builder.append(tok)
                            builder.pop()
                        }
                        tok.contains("=") -> {
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = AttrColor))
                            builder.append(tok)
                            builder.pop()
                        }
                        else -> {
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TagColor))
                            builder.append(tok)
                            builder.pop()
                        }
                    }
                } else {
                    builder.append(tok)
                }
                currentToken = StringBuilder()
            }
        }

        while (index < line.length) {
            val char = line[index]
            if (insideString) {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                builder.append(char)
                builder.pop()
                if (char == stringChar) {
                    insideString = false
                }
                index++
            } else {
                when (char) {
                    '<' -> {
                        flushToken()
                        insideTag = true
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TagColor, fontWeight = FontWeight.Bold))
                        builder.append('<')
                        builder.pop()
                        index++
                    }
                    '>' -> {
                        flushToken()
                        insideTag = false
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TagColor, fontWeight = FontWeight.Bold))
                        builder.append('>')
                        builder.pop()
                        index++
                    }
                    '"', '\'' -> {
                        flushToken()
                        insideString = true
                        stringChar = char
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                        builder.append(char)
                        builder.pop()
                        index++
                    }
                    ' ', '\t', '\n', '\r' -> {
                        flushToken()
                        builder.append(char)
                        index++
                    }
                    else -> {
                        currentToken.append(char)
                        index++
                    }
                }
            }
        }
        flushToken()
        return builder.toAnnotatedString()
    }

    private fun highlightJson(line: String): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        var index = 0
        var insideString = false
        var isKey = true
        var currentToken = StringBuilder()

        fun flushToken() {
            if (currentToken.isNotEmpty()) {
                val tok = currentToken.toString()
                when {
                    tok.all { it.isDigit() } -> {
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = NumberColor))
                        builder.append(tok)
                        builder.pop()
                    }
                    tok == "true" || tok == "false" || tok == "null" -> {
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                        builder.append(tok)
                        builder.pop()
                    }
                    else -> {
                        builder.append(tok)
                    }
                }
                currentToken = StringBuilder()
            }
        }

        while (index < line.length) {
            val char = line[index]
            if (insideString) {
                val col = if (isKey) TypeColor else StringColor
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = col))
                builder.append(char)
                builder.pop()
                if (char == '"' && (index == 0 || line[index - 1] != '\\')) {
                    insideString = false
                }
                index++
            } else {
                when (char) {
                    '"' -> {
                        flushToken()
                        insideString = true
                        val col = if (isKey) TypeColor else StringColor
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = col))
                        builder.append(char)
                        builder.pop()
                        index++
                    }
                    ':' -> {
                        flushToken()
                        isKey = false
                        builder.append(':')
                        index++
                    }
                    ',' -> {
                        flushToken()
                        isKey = true
                        builder.append(',')
                        index++
                    }
                    '{', '}', '[', ']' -> {
                        flushToken()
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                        builder.append(char)
                        builder.pop()
                        index++
                    }
                    ' ', '\t' -> {
                        flushToken()
                        builder.append(char)
                        index++
                    }
                    else -> {
                        currentToken.append(char)
                        index++
                    }
                }
            }
        }
        flushToken()
        return builder.toAnnotatedString()
    }

    private fun highlightYaml(line: String): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        val trimmed = line.trimStart()
        val colonIdx = trimmed.indexOf(":")
        
        if (trimmed.startsWith("#")) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = CommentColor))
            builder.append(line)
            builder.pop()
            return builder.toAnnotatedString()
        }

        if (colonIdx != -1) {
            // Key is everything before colon
            val leadingWhitespace = line.length - trimmed.length
            builder.append(line.substring(0, leadingWhitespace))
            
            val key = trimmed.substring(0, colonIdx)
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TypeColor, fontWeight = FontWeight.Bold))
            builder.append(key)
            builder.pop()
            
            builder.append(":")
            
            val valPart = trimmed.substring(colonIdx + 1)
            var valIdx = 0
            var insideValString = false
            var currentToken = StringBuilder()

            fun flushToken() {
                if (currentToken.isNotEmpty()) {
                    val tok = currentToken.toString()
                    when {
                        tok.trim().all { it.isDigit() } -> {
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = NumberColor))
                            builder.append(tok)
                            builder.pop()
                        }
                        tok.trim() == "true" || tok.trim() == "false" || tok.trim() == "null" || tok.trim() == "yes" || tok.trim() == "no" -> {
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                            builder.append(tok)
                            builder.pop()
                        }
                        else -> {
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                            builder.append(tok)
                            builder.pop()
                        }
                    }
                    currentToken = StringBuilder()
                }
            }

            while (valIdx < valPart.length) {
                val char = valPart[valIdx]
                if (insideValString) {
                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                    builder.append(char)
                    builder.pop()
                    if (char == '"' || char == '\'') {
                        insideValString = false
                    }
                    valIdx++
                } else {
                    if (char == '"' || char == '\'') {
                        flushToken()
                        insideValString = true
                        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                        builder.append(char)
                        builder.pop()
                        valIdx++
                    } else {
                        currentToken.append(char)
                        valIdx++
                    }
                }
            }
            flushToken()
        } else {
            builder.append(line)
        }
        return builder.toAnnotatedString()
    }

    private fun highlightMarkdown(line: String): androidx.compose.ui.text.AnnotatedString {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = TypeColor, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                builder.append(line)
                builder.pop()
            }
            trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith(">") -> {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                builder.append(line.take(line.length - trimmed.length + 1))
                builder.pop()
                builder.append(line.substring(line.length - trimmed.length + 1))
            }
            trimmed.startsWith("`") -> {
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                builder.append(line)
                builder.pop()
            }
            else -> {
                var index = 0
                var insideInlineCode = false
                var insideBold = false
                var currentToken = StringBuilder()

                fun flushToken() {
                    if (currentToken.isNotEmpty()) {
                        val tok = currentToken.toString()
                        when {
                            insideInlineCode -> builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                            insideBold -> builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = DefaultColor, fontWeight = FontWeight.Bold))
                            else -> builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = DefaultColor))
                        }
                        builder.append(tok)
                        builder.pop()
                        currentToken = StringBuilder()
                    }
                }

                while (index < line.length) {
                    val char = line[index]
                    when (char) {
                        '`' -> {
                            flushToken()
                            insideInlineCode = !insideInlineCode
                            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = StringColor))
                            builder.append('`')
                            builder.pop()
                            index++
                        }
                        '*' -> {
                            // Check for ** (bold)
                            if (index + 1 < line.length && line[index + 1] == '*') {
                                flushToken()
                                insideBold = !insideBold
                                builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                                builder.append("**")
                                builder.pop()
                                index += 2
                            } else {
                                currentToken.append('*')
                                index++
                            }
                        }
                        else -> {
                            currentToken.append(char)
                            index++
                        }
                    }
                }
                flushToken()
            }
        }
        return builder.toAnnotatedString()
    }
}

data class ZipNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long
)

fun getMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)
    return when (ext) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/avi"
        "mov" -> "video/quicktime"
        "txt" -> "text/plain"
        "html" -> "text/html"
        "css" -> "text/css"
        "js", "ts" -> "application/javascript"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }
}

@Composable
fun ZipExplorerView(
    filePath: String,
    fileName: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentFolder by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    
    // Loaded zip entries and state
    val zipFile = remember(filePath) {
        try {
            java.util.zip.ZipFile(File(filePath))
        } catch (e: Exception) {
            null
        }
    }
    
    DisposableEffect(filePath) {
        onDispose {
            try {
                zipFile?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Active sub-preview inside zip file
    var activePreviewFile by remember { mutableStateOf<ActiveViewerFile?>(null) }

    if (zipFile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error opening Zip/Archive file", color = Color.White)
        }
        return
    }

    if (activePreviewFile != null) {
        // Render sub-preview overlay inside the ZIP explorer!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with custom Close back to Archive
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { activePreviewFile = null }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Archive",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activePreviewFile!!.fileName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Preview in Archive",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                    IconButton(onClick = { activePreviewFile = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Preview", tint = Color.White)
                    }
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    val preview = activePreviewFile!!
                    val lowerName = preview.fileName.lowercase()
                    val lowerMime = preview.mimeType.lowercase()
                    val isTextOrCode = lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                            lowerName.endsWith(".xml") || lowerName.endsWith(".json") ||
                            lowerName.endsWith(".txt") || lowerName.endsWith(".html") ||
                            lowerName.endsWith(".css") || lowerName.endsWith(".js") ||
                            lowerName.endsWith(".md") || lowerName.endsWith(".properties") ||
                            lowerName.endsWith(".gradle") || lowerName.endsWith(".kts") ||
                            lowerMime.startsWith("text/") || lowerMime.contains("javascript") ||
                            lowerMime.contains("json") || lowerMime.contains("xml")

                    when {
                        preview.mimeType.startsWith("video/") -> {
                            com.swift.browser.videoengine.ui.StandalonePlayerScreen(
                                videoUrl = preview.filePath,
                                videoTitle = preview.fileName,
                                onBack = { activePreviewFile = null }
                            )
                        }
                        preview.mimeType.startsWith("audio/") -> {
                            AudioHostView(
                                filePath = preview.filePath,
                                fileName = preview.fileName,
                                onClose = { activePreviewFile = null }
                            )
                        }
                        preview.mimeType.contains("pdf") -> PdfRendererView(filePath = preview.filePath)
                        preview.mimeType.startsWith("image/") || isImageFile(preview.fileName) -> {
                            ImageOfflineViewer(filePath = preview.filePath, fileName = preview.fileName, mimeType = preview.mimeType)
                        }
                        isTextOrCode -> {
                            TextCodeRendererView(filePath = preview.filePath, fileName = preview.fileName, mimeType = preview.mimeType)
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No high-fidelity browser viewing available for this file type inside archives.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Main ZIP explorer contents
    // Let's first parse items in currentFolder and support search filtration
    val rawCurrentNodes = remember(zipFile, currentFolder) {
        val nodes = mutableMapOf<String, ZipNode>()
        try {
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                val normalized = if (name.endsWith("/")) name else name

                if (currentFolder.isEmpty()) {
                    val firstSlash = normalized.indexOf('/')
                    if (firstSlash == -1) {
                        nodes[name] = ZipNode(name, name, false, entry.size, entry.compressedSize)
                    } else {
                        val folderName = normalized.substring(0, firstSlash + 1)
                        nodes[folderName] = ZipNode(folderName, folderName, true, 0L, 0L)
                    }
                } else {
                    if (normalized.startsWith(currentFolder) && normalized != currentFolder) {
                        val relative = normalized.substring(currentFolder.length)
                        val firstSlash = relative.indexOf('/')
                        if (firstSlash == -1) {
                            nodes[relative] = ZipNode(relative, normalized, false, entry.size, entry.compressedSize)
                        } else {
                            val folderName = relative.substring(0, firstSlash + 1)
                            val fullFolder = currentFolder + folderName
                            nodes[folderName] = ZipNode(folderName, fullFolder, true, 0L, 0L)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        nodes.values.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    val filteredCurrentNodes = remember(rawCurrentNodes, searchQuery) {
        if (searchQuery.isBlank()) rawCurrentNodes
        else rawCurrentNodes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Breadcrumbs & Search
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Breadcrumbs row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "Zip Root",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .clickable { currentFolder = "" }
                            .size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "/",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    if (currentFolder.isNotEmpty()) {
                        val parts = currentFolder.split("/").filter { it.isNotEmpty() }
                        var acc = ""
                        parts.forEachIndexed { idx, part ->
                            acc += "$part/"
                            val targetAcc = acc
                            Text(
                                text = part,
                                color = if (idx == parts.lastIndex) Color(0xFF38BDF8) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { currentFolder = targetAcc }
                            )
                            if (idx < parts.lastIndex) {
                                Text(
                                    text = " > ",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Root Folder",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field inside ZIP explorer
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files in current folder...", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF2E2E),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Back navigation button
        if (currentFolder.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val parts = currentFolder.dropLast(1).split("/")
                        currentFolder = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/", postfix = "/")
                    }
                    .padding(vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Up", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("... Go Up One Level", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // List of entries in current folder
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredCurrentNodes) { node ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (node.isDirectory) {
                                currentFolder = node.fullPath
                                searchQuery = ""
                            } else {
                                // Extract file to temp cache for direct preview!
                                try {
                                    val entry = zipFile.getEntry(node.fullPath)
                                    if (entry != null) {
                                        val tempDir = File(context.cacheDir, "zip_temp_previews")
                                        if (!tempDir.exists()) tempDir.mkdirs()
                                        
                                        // Clean cache if too many previews accumulated
                                        tempDir.listFiles()?.forEach { it.delete() }
                                        
                                        val cleanName = node.name.substringAfterLast("/")
                                        val tempFile = File(tempDir, cleanName)
                                        zipFile.getInputStream(entry).use { input ->
                                            tempFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        val mime = getMimeType(cleanName)
                                        activePreviewFile = ActiveViewerFile(
                                            filePath = tempFile.absolutePath,
                                            fileName = cleanName,
                                            mimeType = mime
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot preview file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val icon = if (node.isDirectory) Icons.Default.Folder else {
                                val mime = getMimeType(node.name)
                                when {
                                    mime.startsWith("image/") -> Icons.Default.Image
                                    mime.startsWith("video/") -> Icons.Default.Movie
                                    mime.startsWith("audio/") -> Icons.Default.MusicNote
                                    mime.contains("pdf") -> Icons.Default.PictureAsPdf
                                    else -> Icons.Default.InsertDriveFile
                                }
                            }
                            val tint = if (node.isDirectory) Color(0xFFFFD700) else Color(0xFF818CF8)
                            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (node.isDirectory) node.name.dropLast(1) else node.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!node.isDirectory) {
                                    Text(
                                        text = "Size: ${formatFileSize(node.size)} (Compressed: ${formatFileSize(node.compressedSize)})",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        
                        if (!node.isDirectory) {
                            IconButton(
                                onClick = {
                                    try {
                                        val entry = zipFile.getEntry(node.fullPath)
                                        if (entry != null) {
                                            val category = DownloadScheduler.getCategoryForMimeType(getMimeType(node.name))
                                            val outDir = File(DownloadScheduler.getDownloadDirectory(context, category), "Extracted")
                                            if (!outDir.exists()) outDir.mkdirs()
                                            
                                            val flatName = node.name.substringAfterLast("/")
                                            val outFile = File(outDir, flatName)
                                            zipFile.getInputStream(entry).use { input ->
                                                outFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            android.widget.Toast.makeText(context, "Extracted to: ${outFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Extraction failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Unarchive, contentDescription = "Extract file", tint = Color(0xFF50FA7B), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


