package com.swift.browser.notificationengine.api

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Public API Gateway for Notification Engine.
 * Allows browser shell to open Notification Center, inject polyfills, and delegate lifecycle hooks
 * without owning notification UI or business logic.
 */
interface NotificationEngineApi {
    fun init(context: Context)
    fun getJavascriptPolyfill(context: Context, websiteUrl: String, callback: (String) -> Unit)
    
    suspend fun setWebsiteEnabled(context: Context, websiteUrl: String, enabled: Boolean)
    suspend fun setMuted(context: Context, websiteUrl: String, muted: Boolean)
    suspend fun setPriority(context: Context, websiteUrl: String, priority: Int)
    suspend fun setSoundEnabled(context: Context, websiteUrl: String, enabled: Boolean)
    suspend fun setVibrationEnabled(context: Context, websiteUrl: String, enabled: Boolean)
    suspend fun pauseNotifications(context: Context, websiteUrl: String, durationMs: Long)
    suspend fun resumeNotifications(context: Context, websiteUrl: String)
    suspend fun postWebNotification(context: Context, websiteUrl: String, websiteName: String, title: String, body: String, clickUrl: String)
    fun forceSync(context: Context)

    // Download Notification API
    fun showDownloadNotification(context: Context, downloadItem: com.swift.browser.downloadengine.DownloadItem)
    fun cancelDownloadNotification(context: Context, downloadId: Long)

    // Media Notification API
    fun showMediaNotification(context: Context, title: String, isPlaying: Boolean)
    fun clearMediaNotification(context: Context)
    fun setMediaActionListener(listener: (String) -> Unit)
    
    @Composable
    fun NotificationCenterUi(
        onBack: () -> Unit,
        onOpenUrl: (String) -> Unit,
        modifier: Modifier
    )
}

object NotificationEngineProvider {
    var api: NotificationEngineApi = DefaultNotificationEngineApi()
}

class DefaultNotificationEngineApi : NotificationEngineApi {
    override fun init(context: Context) {
        com.swift.browser.notificationengine.BackgroundNotificationService.startEngine(context)
    }

    override fun getJavascriptPolyfill(context: Context, websiteUrl: String, callback: (String) -> Unit) {
        com.swift.browser.notificationengine.NotificationEngineImpl(context)
            .getJavascriptPolyfill(websiteUrl, callback)
    }

    override suspend fun setWebsiteEnabled(context: Context, websiteUrl: String, enabled: Boolean) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        val sub = settings.getSubscription(websiteUrl)
        if (sub != null) {
            settings.updateSubscription(sub.copy(enabled = enabled))
        }
    }

    override suspend fun setMuted(context: Context, websiteUrl: String, muted: Boolean) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        val sub = settings.getSubscription(websiteUrl)
        if (sub != null) {
            settings.updateSubscription(sub.copy(isMuted = muted))
        }
    }

    override suspend fun setPriority(context: Context, websiteUrl: String, priority: Int) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        val sub = settings.getSubscription(websiteUrl)
        if (sub != null) {
            settings.updateSubscription(sub.copy(priority = priority))
        }
    }

    override suspend fun setSoundEnabled(context: Context, websiteUrl: String, enabled: Boolean) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        val sub = settings.getSubscription(websiteUrl)
        if (sub != null) {
            settings.updateSubscription(sub.copy(soundEnabled = enabled))
        }
    }

    override suspend fun setVibrationEnabled(context: Context, websiteUrl: String, enabled: Boolean) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        val sub = settings.getSubscription(websiteUrl)
        if (sub != null) {
            settings.updateSubscription(sub.copy(vibrationEnabled = enabled))
        }
    }

    override suspend fun pauseNotifications(context: Context, websiteUrl: String, durationMs: Long) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        settings.pauseNotifications(websiteUrl, durationMs)
    }

    override suspend fun resumeNotifications(context: Context, websiteUrl: String) {
        val settings = com.swift.browser.notificationengine.NotificationSettingsManager(context)
        settings.resumeNotifications(websiteUrl)
    }

    override suspend fun postWebNotification(context: Context, websiteUrl: String, websiteName: String, title: String, body: String, clickUrl: String) {
        com.swift.browser.notificationengine.showWebNotificationHelper(
            context = context,
            websiteUrl = websiteUrl,
            websiteName = websiteName,
            title = title,
            body = body,
            clickUrl = clickUrl
        )
    }

    override fun forceSync(context: Context) {
        com.swift.browser.notificationengine.BackgroundNotificationService.forceImmediateSync(context)
    }

    override fun showDownloadNotification(context: Context, downloadItem: com.swift.browser.downloadengine.DownloadItem) {
        com.swift.browser.notificationengine.download.DownloadNotificationManager.showDownloadNotification(context, downloadItem)
    }

    override fun cancelDownloadNotification(context: Context, downloadId: Long) {
        com.swift.browser.notificationengine.download.DownloadNotificationManager.cancelNotification(context, downloadId)
    }

    override fun showMediaNotification(context: Context, title: String, isPlaying: Boolean) {
        com.swift.browser.notificationengine.media.MediaNotificationBridge.showPlaybackNotification(context, title, isPlaying)
    }

    override fun clearMediaNotification(context: Context) {
        com.swift.browser.notificationengine.media.MediaNotificationBridge.clearNotification(context)
    }

    override fun setMediaActionListener(listener: (String) -> Unit) {
        com.swift.browser.notificationengine.media.MediaNotificationBridge.setMediaActionListener(listener)
    }

    @Composable
    override fun NotificationCenterUi(
        onBack: () -> Unit,
        onOpenUrl: (String) -> Unit,
        modifier: Modifier
    ) {
        com.swift.browser.notificationengine.NotificationCenterScreen(
            onBack = onBack,
            onOpenUrl = onOpenUrl,
            modifier = modifier
        )
    }
}
