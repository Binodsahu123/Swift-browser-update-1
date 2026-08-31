package com.swift.browser.audioengine.online

import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMusicToolbar(
    title: String,
    canGoBack: Boolean,
    isFavorite: Boolean,
    error: String? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: (String) -> Unit,
    onReload: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Online Music", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Song, artist, or album...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            onSearch(searchQuery)
                            showSearchDialog = false
                            searchQuery = ""
                        }
                    }
                ) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    text = title.ifEmpty { "Online Music" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                } else {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                    )
                }
            },
            actions = {
                IconButton(onClick = { showSearchDialog = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = onHome) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                }
                IconButton(onClick = onReload) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFF43F5E) else Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0F172A)
            )
        )

        if (!error.isNullOrBlank()) {
            Surface(
                color = Color(0xFF7F1D1D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onReload) {
                        Text("Retry", color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineMusicWebViewComponent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val webView = OnlineMusicWebViewManager.getOrCreateWebView(ctx)
                (webView.parent as? ViewGroup)?.removeView(webView)
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        update = { frameLayout ->
            val webView = OnlineMusicWebViewManager.getOrCreateWebView(frameLayout.context)
            if (webView.parent != frameLayout) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                frameLayout.removeAllViews()
                frameLayout.addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

