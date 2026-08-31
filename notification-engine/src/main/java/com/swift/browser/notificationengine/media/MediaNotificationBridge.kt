package com.swift.browser.notificationengine.media

import android.content.Context

object MediaNotificationBridge {
    private var engine: MediaNotificationEngine? = null

    private fun getEngine(context: Context): MediaNotificationEngine {
        return engine ?: MediaNotificationEngine(context.applicationContext).also { engine = it }
    }

    fun showPlaybackNotification(context: Context, title: String, isPlaying: Boolean) {
        getEngine(context).showPlaybackNotification(title, isPlaying)
    }

    fun clearNotification(context: Context) {
        getEngine(context).clearNotification()
    }

    fun setMediaActionListener(listener: (String) -> Unit) {
        MediaNotificationReceiver.onMediaAction = listener
    }
}
