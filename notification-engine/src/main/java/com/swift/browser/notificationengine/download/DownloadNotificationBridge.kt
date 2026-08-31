package com.swift.browser.notificationengine.download

import android.content.Context
import com.swift.browser.downloadengine.DownloadItem

object DownloadNotificationBridge {
    fun showNotification(context: Context, item: DownloadItem) {
        DownloadNotificationManager.showDownloadNotification(context, item)
    }

    fun cancelNotification(context: Context, downloadId: Long) {
        DownloadNotificationManager.cancelNotification(context, downloadId)
    }

    fun startForegroundService(context: Context) {
        DownloadForegroundService.startService(context)
    }

    fun stopForegroundService(context: Context) {
        DownloadForegroundService.stopService(context)
    }
}
