package com.swift.browser.vpnengine.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.swift.browser.vpnengine.VpnEngineDependencyContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BatteryOptimizationMode {
    NORMAL,
    POWER_SAVE,
    AGGRESSIVE_POWER_SAVE
}

class BatteryOptimizer(private val context: Context) {
    private val _optimizationMode = MutableStateFlow(BatteryOptimizationMode.NORMAL)
    val optimizationMode: StateFlow<BatteryOptimizationMode> = _optimizationMode.asStateFlow()

    fun checkBatteryState() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Float = level * 100 / scale.toFloat()
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val newMode = when {
            isCharging -> BatteryOptimizationMode.NORMAL
            batteryPct <= 15f -> BatteryOptimizationMode.AGGRESSIVE_POWER_SAVE
            batteryPct <= 30f -> BatteryOptimizationMode.POWER_SAVE
            else -> BatteryOptimizationMode.NORMAL
        }

        if (_optimizationMode.value != newMode) {
            _optimizationMode.value = newMode
            VpnEngineDependencyContainer.stateManager.addLog("Battery Optimizer changed mode to ${newMode.name} (Battery: ${batteryPct.toInt()}%, Charging: $isCharging)", "INFO")
        }
    }
    
    fun getRecommendedHealthCheckIntervalMs(): Long {
        return when (_optimizationMode.value) {
            BatteryOptimizationMode.NORMAL -> 1000L
            BatteryOptimizationMode.POWER_SAVE -> 5000L
            BatteryOptimizationMode.AGGRESSIVE_POWER_SAVE -> 15000L
        }
    }
}
