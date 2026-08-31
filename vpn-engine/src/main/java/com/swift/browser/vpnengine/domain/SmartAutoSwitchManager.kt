package com.swift.browser.vpnengine.domain

import android.util.Log

class SmartAutoSwitchManager {
    fun shouldSwitch(healthStats: VpnHealthStats): Boolean {
        if (healthStats.status == "Critical" || healthStats.packetLoss > 5.0f || healthStats.ping > 300) {
            Log.i("SmartAutoSwitch", "Ping is too high (${healthStats.ping}ms), suggesting switch.")
            return true
        }
        return false
    }
}
