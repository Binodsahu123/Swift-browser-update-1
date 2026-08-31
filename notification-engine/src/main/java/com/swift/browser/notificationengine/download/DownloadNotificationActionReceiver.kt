package com.swift.browser.notificationengine.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swift.browser.downloadengine.DownloadManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val downloadId = intent.getLongExtra("download_id", -1L)
        if (downloadId == -1L) return

        val appScope = CoroutineScope(Dispatchers.Default)
        val engine = DownloadManagerImpl(context.applicationContext)

        when (intent.action) {
            "com.swift.browser.download.ACTION_PAUSE" -> {
                appScope.launch {
                    engine.pauseDownload(downloadId)
                }
            }
            "com.swift.browser.download.ACTION_RESUME" -> {
                appScope.launch {
                    engine.resumeDownload(downloadId)
                }
            }
            "com.swift.browser.download.ACTION_CANCEL" -> {
                appScope.launch {
                    engine.cancelDownload(downloadId)
                }
            }
        }
    }
}
