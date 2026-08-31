package com.swift.browser.notificationengine.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.swift.browser.downloadengine.DownloadItem

object DownloadNotificationManager {
    private const val CHANNEL_ID = "swift_browser_downloads_channel"
    private const val CHANNEL_NAME = "Active Downloads"
    
    fun createNotificationChannel(context: Context) {
        com.swift.browser.notificationengine.NotificationChannelManager.createNotificationChannels(context)
    }

    fun showDownloadNotification(context: Context, item: DownloadItem) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(context.packageName)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
            action = "com.swift.browser.download.ACTION_PAUSE"
            putExtra("download_id", item.id)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt() + 1000,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val resumeIntent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
            action = "com.swift.browser.download.ACTION_RESUME"
            putExtra("download_id", item.id)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt() + 2000,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val cancelIntent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
            action = "com.swift.browser.download.ACTION_CANCEL"
            putExtra("download_id", item.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt() + 3000,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: ${item.title}")
            .setContentText("${item.progress}% • ${item.speed}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(item.status == "COMPLETED" || item.status == "FAILED")

        when (item.status) {
            "RUNNING" -> {
                builder.setProgress(100, item.progress, false)
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            }
            "PAUSED" -> {
                builder.setSmallIcon(android.R.drawable.ic_media_play)
                builder.setContentText("Paused • ${item.progress}%")
                builder.setProgress(100, item.progress, true)
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            }
            "COMPLETED" -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentTitle("Download Complete")
                builder.setContentText(item.title)
                builder.setProgress(0, 0, false)
            }
            "FAILED" -> {
                builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                builder.setContentTitle("Download Failed")
                builder.setContentText("${item.title} - ${item.speed}")
                builder.setProgress(0, 0, false)
            }
            "CANCELLED" -> {
                manager.cancel(item.id.toInt())
                return
            }
        }

        manager.notify(item.id.toInt(), builder.build())
    }

    fun cancelNotification(context: Context, downloadId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(downloadId.toInt())
    }
}
