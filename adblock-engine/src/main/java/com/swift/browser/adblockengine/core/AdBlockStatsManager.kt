package com.swift.browser.adblockengine.core

import android.content.Context
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistently tracks metrics and statistics of blocked trackers, network ads, and element hides.
 */
object AdBlockStatsManager {
    private val _totalBlockedFlow = MutableStateFlow(0)
    val totalBlockedFlow: StateFlow<Int> = _totalBlockedFlow.asStateFlow()

    private val adsBlocked = AtomicInteger(0)
    private val trackersBlocked = AtomicInteger(0)
    private val cosmeticHides = AtomicInteger(0)

    fun init(context: Context) {
        adsBlocked.set(AdBlockPreferenceStore.getInt(context, "stats_ads_blocked", 14832))
        trackersBlocked.set(AdBlockPreferenceStore.getInt(context, "stats_trackers_blocked", 8392))
        cosmeticHides.set(AdBlockPreferenceStore.getInt(context, "stats_cosmetic_hides", 23941))
        _totalBlockedFlow.value = getTotalBlocked()
    }

    fun getAdsBlocked(): Int = adsBlocked.get()
    fun getTrackersBlocked(): Int = trackersBlocked.get()
    fun getCosmeticHides(): Int = cosmeticHides.get()
    
    fun getTotalBlocked(): Int = adsBlocked.get() + trackersBlocked.get()

    fun recordAdBlocked() {
        adsBlocked.incrementAndGet()
        _totalBlockedFlow.value = getTotalBlocked()
    }

    fun recordTrackerBlocked() {
        trackersBlocked.incrementAndGet()
        _totalBlockedFlow.value = getTotalBlocked()
    }

    fun recordCosmeticHide(count: Int) {
        cosmeticHides.addAndGet(count)
        _totalBlockedFlow.value = getTotalBlocked()
    }

    fun saveStats(context: Context) {
        AdBlockPreferenceStore.saveInt(context, "stats_ads_blocked", adsBlocked.get())
        AdBlockPreferenceStore.saveInt(context, "stats_trackers_blocked", trackersBlocked.get())
        AdBlockPreferenceStore.saveInt(context, "stats_cosmetic_hides", cosmeticHides.get())
    }
}
