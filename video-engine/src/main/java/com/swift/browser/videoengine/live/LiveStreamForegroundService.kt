package com.swift.browser.videoengine.live

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.swift.browser.notificationengine.LiveStreamNotificationHelper

class LiveStreamForegroundService : Service() {

    companion object {
        private const val TAG = "LiveStreamFgService"
        const val NOTIFICATION_ID = 10101
        
        const val ACTION_START = "com.swift.browser.action.START_LIVE_STREAM"
        const val ACTION_STOP = "com.swift.browser.action.STOP_LIVE_STREAM"

        fun startService(context: Context) {
            val intent = Intent(context, LiveStreamForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LiveStreamForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        if (action == ACTION_STOP) {
            Log.i(TAG, "Stopping LiveStreamForegroundService via ACTION_STOP")
            LiveStreamingEngine.stopSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val activeSession = LiveStreamingEngine.getActiveSession()
        if (activeSession == null) {
            Log.w(TAG, "Process recreated or service restarted without active stream session. Stopping self.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Preparing Live Stream...", false)

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )

        // Observe state changes to update notification
        LiveStreamingEngine.registerStateListener(::onStateChanged)

        return START_STICKY
    }

    private fun onStateChanged(state: LiveStreamState) {
        val statusText = when (state) {
            LiveStreamState.IDLE -> "Idle"
            LiveStreamState.PREPARING -> "Preparing resources..."
            LiveStreamState.INITIALIZING_VIDEO -> "Initializing video encoder..."
            LiveStreamState.INITIALIZING_AUDIO -> "Initializing audio encoder..."
            LiveStreamState.ENCODING -> "Encoding live streams..."
            LiveStreamState.CONNECTING -> "Connecting to stream server..."
            LiveStreamState.STREAMING -> "Streaming Live!"
            LiveStreamState.RECONNECTING -> "Network lost. Reconnecting..."
            LiveStreamState.STOPPING -> "Stopping stream..."
            LiveStreamState.STOPPED -> "Stream Stopped"
            LiveStreamState.FAILED -> "Stream Failed"
        }

        val isStreaming = (state == LiveStreamState.STREAMING)
        
        if (state == LiveStreamState.STOPPED || state == LiveStreamState.FAILED || state == LiveStreamState.IDLE) {
            Log.i(TAG, "Terminal state $state detected. Stopping foreground service.")
            LiveStreamNotificationHelper.resetDuration()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification(statusText, isStreaming)
        }
    }

    private fun updateNotification(content: String, isStreaming: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(content, isStreaming))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "LiveStreamForegroundService destroyed")
        LiveStreamingEngine.unregisterStateListener(::onStateChanged)
        LiveStreamingEngine.onServiceDestruction()
        super.onDestroy()
    }

    private fun buildNotification(content: String, isStreaming: Boolean): Notification {
        val stopIntent = Intent(this, LiveStreamForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent().apply {
            setClassName(packageName, "com.swift.browser.MainActivity")
        }
        val pendingMainIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return LiveStreamNotificationHelper.buildNotification(
            context = this,
            stateText = content,
            isStreaming = isStreaming,
            stopIntent = pendingStopIntent,
            mainIntent = pendingMainIntent
        )
    }
}
