package com.swift.browser.desktopengine.rules

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.swift.browser.desktopengine.api.DesktopDefaultMode
import com.swift.browser.desktopengine.api.DesktopMode

data class DeviceMetrics(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val density: Float,
    val ramMb: Long,
    val isTablet: Boolean,
    val isExternalDisplay: Boolean
)

object AutoDesktopPolicy {
    var minTabletWidthDp: Int = 600
    var minDesktopRamMb: Long = 3072L // 3GB RAM threshold

    fun getDeviceMetrics(context: Context): DeviceMetrics {
        val config = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val smallestWidthDp = config.smallestScreenWidthDp
        val isTablet = smallestWidthDp >= minTabletWidthDp || (config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val ramMb = memoryInfo.totalMem / (1024 * 1024)

        return DeviceMetrics(
            screenWidthDp = config.screenWidthDp,
            screenHeightDp = config.screenHeightDp,
            density = metrics.density,
            ramMb = ramMb,
            isTablet = isTablet,
            isExternalDisplay = false
        )
    }

    fun evaluate(metrics: DeviceMetrics, userPreference: DesktopDefaultMode = DesktopDefaultMode.AUTO): DesktopMode {
        return when (userPreference) {
            DesktopDefaultMode.DESKTOP -> DesktopMode.DESKTOP
            DesktopDefaultMode.MOBILE -> DesktopMode.MOBILE
            DesktopDefaultMode.AUTO -> {
                if (metrics.isTablet || metrics.isExternalDisplay || (metrics.screenWidthDp >= minTabletWidthDp && metrics.ramMb >= minDesktopRamMb)) {
                    DesktopMode.DESKTOP
                } else {
                    DesktopMode.MOBILE
                }
            }
        }
    }

    fun evaluate(context: Context, userPreference: DesktopDefaultMode = DesktopDefaultMode.AUTO): DesktopMode {
        val metrics = getDeviceMetrics(context)
        return evaluate(metrics, userPreference)
    }
}
