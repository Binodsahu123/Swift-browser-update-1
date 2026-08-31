package com.swift.browser.notificationengine.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class MediaNotificationReceiver : BroadcastReceiver() {
    companion object {
        var onMediaAction: ((String) -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        onMediaAction?.invoke(action)
    }
}

class MediaNotificationEngine(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "swift_browser_media"

    init {
        com.swift.browser.notificationengine.NotificationChannelManager.createNotificationChannels(context)
    }

    fun showPlaybackNotification(title: String, isPlaying: Boolean) {
        val playPauseIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = "ACTION_PLAY_PAUSE"
        }
        val playPausePending = PendingIntent.getBroadcast(
            context, 101, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = "ACTION_NEXT"
        }
        val nextPending = PendingIntent.getBroadcast(
            context, 102, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = "ACTION_PREVIOUS"
        }
        val prevPending = PendingIntent.getBroadcast(
            context, 103, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = "ACTION_CLOSE"
        }
        val closePending = PendingIntent.getBroadcast(
            context, 104, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText("Playing in SwiftBrowser")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePending
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", closePending)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(7751, notification)
    }

    fun clearNotification() {
        notificationManager.cancel(7751)
    }
}
