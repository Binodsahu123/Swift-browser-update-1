package com.swift.browser.notificationengine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    private val TAG = "NotificationReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "NotificationReceiver received system broadcast action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device booted up! Initializing Notification engine sync schedulers...")
            BackgroundNotificationService.startEngine(context)
        }
    }
}
