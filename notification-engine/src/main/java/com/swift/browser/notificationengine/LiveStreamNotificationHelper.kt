package com.swift.browser.notificationengine

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat

object LiveStreamNotificationHelper {
    const val CHANNEL_ID = "live_stream_channel"
    const val NOTIFICATION_ID = 10101
    
    @Volatile
    private var streamStartRealtime: Long = 0L

    fun buildNotification(
        context: Context,
        stateText: String,
        isStreaming: Boolean,
        stopIntent: PendingIntent,
        mainIntent: PendingIntent
    ): Notification {
        // Enforce channel configuration
        NotificationChannelManager.createNotificationChannels(context)

        val title = if (isStreaming) "🔴 LIVE Direct Streaming" else "Direct Live Streaming"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(stateText)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Stream",
                stopIntent
            )

        if (isStreaming) {
            if (streamStartRealtime == 0L) {
                streamStartRealtime = SystemClock.elapsedRealtime()
            }
            // Use chronometer for real-time tick-by-tick duration display
            builder.setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - streamStartRealtime))
            builder.setShowWhen(true)
            builder.setUsesChronometer(true)
        } else {
            streamStartRealtime = 0L
            builder.setShowWhen(false)
            builder.setUsesChronometer(false)
        }

        return builder.build()
    }

    fun resetDuration() {
        streamStartRealtime = 0L
    }
}
