package com.swift.browser.notificationengine.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.swift.browser.downloadengine.DownloadDatabase
import com.swift.browser.downloadengine.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "swift_browser_downloads_channel"
        private const val CHANNEL_NAME = "Active Downloads"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createInitialNotification())

        serviceScope.launch {
            try {
                val dao = DownloadDatabase.getDatabase(applicationContext).downloadDao()
                dao.getAllDownloadsFlow().collectLatest { downloads ->
                    val activeDownloads = downloads.filter { it.status == "RUNNING" || it.status == "PAUSED" }
                    if (activeDownloads.isEmpty()) {
                        stopSelf()
                    } else {
                        activeDownloads.forEach { item ->
                            DownloadNotificationManager.showDownloadNotification(applicationContext, item)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        DownloadNotificationManager.createNotificationChannel(applicationContext)
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SwiftBrowser Downloads")
            .setContentText("Managing background downloads...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
