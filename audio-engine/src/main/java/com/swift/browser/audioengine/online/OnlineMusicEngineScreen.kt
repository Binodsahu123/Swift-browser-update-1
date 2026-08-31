package com.swift.browser.audioengine.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun OnlineMusicEngineScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title by OnlineMusicWebViewManager.currentTitle.collectAsState()
    val isFavorite by OnlineMusicWebViewManager.isFavorite.collectAsState()
    val onlineError by OnlineMusicWebViewManager.onlineError.collectAsState()
    val canGoBack = OnlineMusicWebViewManager.canGoBack()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        OnlineMusicToolbar(
            title = title,
            canGoBack = canGoBack,
            isFavorite = isFavorite,
            error = onlineError,
            onBack = { OnlineMusicWebViewManager.goBack() },
            onHome = { OnlineMusicWebViewManager.loadHome(context) },
            onSearch = { query -> OnlineMusicWebViewManager.search(query, context) },
            onReload = { OnlineMusicWebViewManager.reload() },
            onToggleFavorite = { OnlineMusicWebViewManager.toggleFavorite(context) }
        )

        OnlineMusicWebViewComponent(
            modifier = Modifier.weight(1f)
        )
    }
}

