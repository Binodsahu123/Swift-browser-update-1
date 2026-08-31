package com.swift.browser.downloadengine

import android.content.Context

object DownloadEngineProvider {
    @Volatile
    private var instance: DownloadEngine? = null

    fun getEngine(context: Context): DownloadEngine {
        return instance ?: synchronized(this) {
            instance ?: DownloadManagerImpl(context.applicationContext).also { instance = it }
        }
    }
}
