package com.swift.browser.notificationengine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object NotificationChannelManager {
    private const val CHANNEL_DEFAULT_ID = "swift_browser_notifications"
    private const val CHANNEL_HIGH_ID = "swift_browser_high_priority"
    private const val CHANNEL_SILENT_ID = "swift_browser_silent"

    /**
     * Set up all default notification channels in the application context.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Default Channel
            val defaultChan = NotificationChannel(
                CHANNEL_DEFAULT_ID,
                "Website Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Receives updates from your permitted websites"
                enableVibration(true)
            }

            // 2. High Priority Channel
            val highChan = NotificationChannel(
                CHANNEL_HIGH_ID,
                "High Priority Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "A urgent alert channel for subscribed sources"
                enableLights(true)
                enableVibration(true)
            }

            // 3. Silent/Low Priority Channel
            val silentChan = NotificationChannel(
                CHANNEL_SILENT_ID,
                "Silent Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quietly collects subscriptions and logs feeds"
                enableVibration(false)
                setSound(null, null)
            }

            manager.createNotificationChannel(defaultChan)
            manager.createNotificationChannel(highChan)
            manager.createNotificationChannel(silentChan)

            // 4. Active Downloads Channel
            val downloadChan = NotificationChannel(
                "swift_browser_downloads_channel",
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and speed for active SwiftBrowser downloads."
            }
            manager.createNotificationChannel(downloadChan)

            // 5. Media Playback Channel
            val mediaChan = NotificationChannel(
                "swift_browser_media",
                "SwiftBrowser Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media controls on the lock screen and notification drawer"
            }
            manager.createNotificationChannel(mediaChan)

            // 6. Direct Live Streaming Channel
            val liveChan = NotificationChannel(
                "live_stream_channel",
                "Direct Live Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status and actions for your active direct live-streaming session"
            }
            manager.createNotificationChannel(liveChan)
        }
    }

    /**
     * Resolves which notification channel is appropriate for a specific website subscription configurations.
     */
    fun getChannelId(priority: Int, soundEnabled: Boolean, vibrationEnabled: Boolean, isMuted: Boolean): String {
        if (isMuted) return CHANNEL_SILENT_ID
        if (!soundEnabled && !vibrationEnabled) return CHANNEL_SILENT_ID
        return if (priority >= 2) CHANNEL_HIGH_ID else CHANNEL_DEFAULT_ID
    }
}
