package com.swift.browser.adblockengine.update

import android.content.Context
import com.swift.browser.adblockengine.core.AdBlockPolicy

/**
 * Strategy determining if update runs now based on network transport types.
 */
object AdBlockNetworkRefreshPolicy {
    fun isNetworkRefreshAllowed(context: Context, manual: Boolean): Boolean {
        if (manual) return true
        if (!AdBlockPolicy.wifiOnlyUpdate) return true

        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            true // fallback safe
        }
    }
}
