package com.swift.browser.vpnengine.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.swift.browser.vpnengine.VpnEngineDependencyContainer

class SmartAlertManager(private val context: Context) {
    private val alertScope = CoroutineScope(Dispatchers.Main + Job())
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    fun startMonitoring() {
        alertScope.launch {
            VpnEngineDependencyContainer.stateManager.healthStats.collectLatest { health ->
                if (health.status == "Critical") {
                    sendAlert("Network Unstable", "Your current connection is highly unstable. Consider switching servers.")
                }
            }
        }
        
        alertScope.launch {
            VpnEngineDependencyContainer.stateManager.connectionState.collectLatest { state ->
                if (state == VpnConnectionState.DISCONNECTED && VpnEngineDependencyContainer.stateManager.settings.value.killSwitch) {
                    sendAlert("VPN Disconnected", "Kill Switch is active. Your internet traffic is blocked to prevent leaks.")
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Smart Alerts"
            val descriptionText = "Important alerts from the AI VPN Manager"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("smart_alerts", name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun sendAlert(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, "smart_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }
}
