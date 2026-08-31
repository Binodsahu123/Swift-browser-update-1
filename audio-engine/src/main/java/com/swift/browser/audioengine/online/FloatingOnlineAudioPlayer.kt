package com.swift.browser.audioengine.online

import androidx.compose.runtime.Composable
import com.swift.browser.audioengine.FloatingOnlineAudioPlayer as CoreFloatingOnlineAudioPlayer

@Composable
fun FloatingOnlineAudioPlayer(
    onNavigateToOnlineMusic: () -> Unit
) {
    CoreFloatingOnlineAudioPlayer(onNavigateToOnlineMusic = onNavigateToOnlineMusic)
}
