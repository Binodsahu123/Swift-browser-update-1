package com.swift.browser.adblockengine.core

import android.content.Context
import android.util.Log
import com.swift.browser.adblockengine.brave.BraveAdblockAdapter
import com.swift.browser.adblockengine.filters.FilterListManager
import com.swift.browser.adblockengine.filters.FilterUpdateScheduler
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main entry point of the upgraded modular adblock subsystem.
 * Coordinates initialization, enable/disable toggle states, and lifecycle syncs.
 */
object AdBlockEngine {
    private const val TAG = "AdBlockEngine"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    var isInitialized = false
        private set

    var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        Log.i(TAG, "Initializing modular Brave-style Ad Block Engine...")

        try {
            val appCtx = context.applicationContext
            appContext = appCtx

            // 1. Initialize persistent storage
            AdBlockPreferenceStore.init(appCtx)

            // 2. Initialize Core Sub-Managers
            AdBlockWhitelistManager.init(appCtx)
            AdBlockExceptionManager.init(appCtx)
            AdBlockStatsManager.init(appCtx)
            AdBlockDiagnostics.init(appCtx)

            // 3. Initialize Filters & Schedules
            FilterListManager.init(appCtx)
            FilterUpdateScheduler.init(appCtx)

            isInitialized = true
            AdBlockDiagnostics.logEvent("Engine Init", "System initialized successfully with new package architectures.")

            // 4. Run asynchronous update if auto-update is active
            if (AdBlockPolicy.autoUpdateEnabled) {
                scope.launch {
                    FilterUpdateScheduler.triggerUpdate(appCtx, manual = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdBlockEngine", e)
            AdBlockDiagnostics.logEvent("Engine Init Failed", "Error: ${e.message}")
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AdBlockPolicy.isEasyListEnabled = enabled
        AdBlockPreferenceStore.saveBoolean(context, "easylist_enabled", enabled)
        AdBlockDiagnostics.logEvent("Engine Toggle", "Global Shields set to: $enabled")
        FilterListManager.rebuildActiveRules(context)
    }

    fun isEnabled(): Boolean {
        return AdBlockPolicy.isEasyListEnabled
    }

    fun refreshFilters(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            FilterUpdateScheduler.triggerUpdate(context, manual = true, onComplete = onComplete)
        }
    }

    fun updatePolicy(context: Context, easyList: Boolean, easyPrivacy: Boolean, autoUpdate: Boolean, wifiOnly: Boolean) {
        AdBlockPolicy.isEasyListEnabled = easyList
        AdBlockPolicy.isEasyPrivacyEnabled = easyPrivacy
        AdBlockPolicy.autoUpdateEnabled = autoUpdate
        AdBlockPolicy.wifiOnlyUpdate = wifiOnly

        AdBlockPreferenceStore.saveBoolean(context, "easylist_enabled", easyList)
        AdBlockPreferenceStore.saveBoolean(context, "easyprivacy_enabled", easyPrivacy)
        AdBlockPreferenceStore.saveBoolean(context, "autoupdate_enabled", autoUpdate)
        AdBlockPreferenceStore.saveBoolean(context, "wifionly_enabled", wifiOnly)

        FilterListManager.rebuildActiveRules(context)
    }

    fun getStatistics(): Map<String, Int> {
        return mapOf(
            "ads_blocked" to AdBlockStatsManager.getAdsBlocked(),
            "trackers_blocked" to AdBlockStatsManager.getTrackersBlocked(),
            "cosmetic_hides" to AdBlockStatsManager.getCosmeticHides(),
            "total_blocked" to AdBlockStatsManager.getTotalBlocked()
        )
    }

    fun getDiagnostics(context: Context): Map<String, Any> {
        return AdBlockDiagnostics.getDiagnosticsReport(context)
    }
}
