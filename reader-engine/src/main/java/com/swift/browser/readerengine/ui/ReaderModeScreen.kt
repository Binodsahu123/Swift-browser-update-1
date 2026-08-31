package com.swift.browser.readerengine.ui

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swift.browser.readerengine.model.ReaderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeScreen(
    state: ReaderState,
    onClose: () -> Unit,
    onUpdateFontSize: (Int) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onUpdateTypeface: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val readerColorTheme = state.theme
    val isSerif = true
    var showPrefsMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val (bgColor, textColor) = when (readerColorTheme) {
        "Light" -> Pair(Color(0xFFFFFFFF), Color(0xFF1E293B))
        "Sepia" -> Pair(Color(0xFFFDF6E3), Color(0xFF586E75))
        else -> Pair(Color(0xFF0F0F10), Color(0xFFE2E8F0))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reader Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Reader Mode",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showPrefsMenu = !showPrefsMenu }) {
                        Icon(
                            imageVector = Icons.Default.TextFormat,
                            contentDescription = "Text Settings",
                            tint = textColor
                        )
                    }
                    
                    IconButton(onClick = { 
                        Toast.makeText(context, "Simplified view activated.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.textZoom = 100
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val urlStr = request.url.toString()
                                if (urlStr.startsWith("swift://search")) {
                                    val q = request.url.getQueryParameter("q") ?: ""
                                    Toast.makeText(context, "Searching: $q", Toast.LENGTH_SHORT).show()
                                    return true
                                }
                                return super.shouldOverrideUrlLoading(view, request)
                            }
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { webView ->
                    val fontStyle = if (isSerif) "Georgia, serif" else "system-ui, -apple-system, sans-serif"
                    
                    val rawTags = state.title.split(" ", ",", "|", "•", "–")
                        .map { it.trim().replace("\"", "").replace("[", "").replace("]", "") }
                        .filter { it.length > 2 && it.length < 18 && !it.contains("http") && !it.any { char -> char.isDigit() } }
                        .distinct()
                    val tags = if (rawTags.isNotEmpty()) rawTags.take(5) else listOf("समाचार", "लाइव अपडेट", "मुख्य खबर")
                        
                    val chipsHtml = """
                    <div class="divider"></div>
                    <div class="related-header">
                        <span class="google-logo">G</span> संबंधित खोजें
                    </div>
                    <div class="chips-container">
                        ${tags.joinToString("") { "<a class=\"chip\" href=\"swift://search?q=${android.net.Uri.encode(it)}\">$it</a>" }}
                    </div>
                    """.trimIndent()

                    val authorText = state.author?.let { "• $it" } ?: ""
                    val dateText = state.date?.let { "• $it" } ?: ""
                    val domainText = state.domain ?: ""

                    val templateHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
                    <style>
                      * {
                        box-sizing: border-box;
                      }
                      body {
                        background-color: ${when (readerColorTheme) { "Light" -> "#FFFFFF" "Sepia" -> "#FDF6E3" else -> "#0F0F10" }};
                        color: ${when (readerColorTheme) { "Light" -> "#1E293B" "Sepia" -> "#586E75" else -> "#E2E8F0" }};
                        font-family: $fontStyle;
                        font-size: ${state.fontSize}px;
                        line-height: 1.62;
                        padding: 12px 16px 80px 16px;
                        margin: 0;
                        word-wrap: break-word;
                      }
                      h1, h2, h3, h4, h5, h6 {
                        color: ${when (readerColorTheme) { "Light" -> "#0F172A" "Sepia" -> "#073642" else -> "#FFFFFF" }};
                        font-family: $fontStyle;
                        font-weight: 800;
                        line-height: 1.25;
                        margin-top: 24px;
                        margin-bottom: 12px;
                      }
                      h1 {
                        font-size: 1.45em;
                        margin-top: 0;
                        margin-bottom: 8px;
                      }
                      .meta-info {
                        font-size: 0.8em;
                        color: ${when (readerColorTheme) { "Light" -> "#64748B" "Sepia" -> "#93A1A1" else -> "#94A3B8" }};
                        margin-bottom: 24px;
                        font-weight: 500;
                      }
                      p {
                        margin-top: 0;
                        margin-bottom: 18px;
                        text-align: left;
                      }
                      img {
                        max-width: 100% !important;
                        height: auto !important;
                        border-radius: 12px;
                        margin: 20px 0;
                        display: block;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.12);
                      }
                      a {
                        color: #6366F1;
                        text-decoration: none;
                        font-weight: 500;
                      }
                      blockquote {
                        border-left: 4px solid #6366F1;
                        padding-left: 16px;
                        margin: 20px 0;
                        font-style: italic;
                        color: #94A3B8;
                      }
                      ol, ul {
                        padding-left: 20px;
                        margin-bottom: 24px;
                      }
                      li {
                        margin-bottom: 8px;
                      }
                      .divider {
                        border-top: 1px solid ${when (readerColorTheme) { "Light" -> "#E2E8F0" "Sepia" -> "#EEE8D5" else -> "#1E293B" }};
                        margin: 24px 0;
                      }
                      .reaction-row {
                        display: flex;
                        align-items: center;
                        gap: 24px;
                        margin: 24px 0;
                      }
                      .reaction-btn {
                        background: none;
                        border: none;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 6px;
                        opacity: 0.73;
                      }
                      .reaction-btn svg {
                        width: 22px;
                        height: 22px;
                        fill: ${when (readerColorTheme) { "Light" -> "#64748B" "Sepia" -> "#586E75" else -> "#94A3B8" }};
                      }
                      .related-header {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        font-weight: 800;
                        font-size: 0.95em;
                        color: ${when (readerColorTheme) { "Light" -> "#0F172A" "Sepia" -> "#073642" else -> "#FFFFFF" }};
                        margin-top: 10px;
                      }
                      .google-logo {
                        color: #4285F4;
                        font-weight: 900;
                        font-size: 1.15em;
                      }
                      .chips-container {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                        margin: 12px 0 24px 0;
                      }
                      .chip {
                        background-color: ${when (readerColorTheme) { "Light" -> "#F1F5F9" "Sepia" -> "#EEE8D5" else -> "#1E293B" }};
                        color: ${when (readerColorTheme) { "Light" -> "#334155" "Sepia" -> "#586E75" else -> "#CBD5E1" }};
                        padding: 8px 16px;
                        border-radius: 20px;
                        font-size: 0.82em;
                        font-weight: 600;
                        border: 1px solid ${when (readerColorTheme) { "Light" -> "#E2E8F0" "Sepia" -> "#E0D7C1" else -> "#334155" }};
                      }
                    </style>
                    </head>
                    <body>
                      <h1>${state.title}</h1>
                      <div class="meta-info">$domainText $authorText $dateText</div>
                      
                      ${state.content}
                      
                      <div class="divider"></div>
                      <div class="reaction-row">
                        <button class="reaction-btn" onclick="alert('Liked')">
                          <svg viewBox="0 0 24 24"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>
                        </button>
                        <button class="reaction-btn" onclick="alert('Disliked')">
                          <svg viewBox="0 0 24 24"><path d="M15 3H6c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v2c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 23l6.59-6.59c.36-.36.58-.86.58-1.41V5c0-1.1-.9-2-2-2zm4 0v12h4V3h-4z"/></svg>
                        </button>
                        <button class="reaction-btn" onclick="alert('Info')">
                          <svg viewBox="0 0 24 24"><path d="M11 9H13V11H11V9ZM11 13H13V17H11V13ZM12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 22 2 12 2ZM12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20Z"/></svg>
                        </button>
                      </div>
                      
                      $chipsHtml
                    </body>
                    </html>
                    """.trimIndent()
                    
                    webView.loadDataWithBaseURL(webView.url, templateHtml, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = showPrefsMenu,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (readerColorTheme) {
                            "Light" -> Color(0xFFF1F5F9)
                            "Sepia" -> Color(0xFFF4EAD4)
                            else -> Color(0xFF1E1E20)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = when (readerColorTheme) {
                            "Light" -> Color(0xFFE2E8F0)
                            "Sepia" -> Color(0xFFE0D7C1)
                            else -> Color(0xFF2E2E32)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Appearance Settings",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            IconButton(
                                onClick = { showPrefsMenu = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close settings",
                                    tint = textColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val themes = listOf("Light", "Sepia", "Dark")
                            themes.forEach { t ->
                                val selected = readerColorTheme == t
                                Card(
                                    onClick = { onUpdateTheme(t) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (t) {
                                            "Light" -> Color.White
                                            "Sepia" -> Color(0xFFFDF6E3)
                                            else -> Color(0xFF0F0F10)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = t,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (t) {
                                                "Light" -> Color(0xFF1E293B)
                                                "Sepia" -> Color(0xFF586E75)
                                                else -> Color(0xFFE2E8F0)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onUpdateTypeface(true) },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(
                                    width = if (isSerif) 2.dp else 1.dp,
                                    color = if (isSerif) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Serif", color = textColor, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { onUpdateTypeface(false) },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(
                                    width = if (!isSerif) 2.dp else 1.dp,
                                    color = if (!isSerif) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Sans-Serif", color = textColor, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Text Size",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledIconButton(
                                    onClick = { if (state.fontSize > 12) onUpdateFontSize(state.fontSize - 2) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textColor.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease text size", tint = textColor)
                                }

                                Text(
                                    text = "${state.fontSize}px",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                FilledIconButton(
                                    onClick = { if (state.fontSize < 28) onUpdateFontSize(state.fontSize + 2) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textColor.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase text size", tint = textColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
