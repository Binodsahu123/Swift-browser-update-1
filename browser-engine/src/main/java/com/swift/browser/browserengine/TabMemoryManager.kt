package com.swift.browser.browserengine

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log

object SystemMemoryManager : ComponentCallbacks2 {

    private const val TAG = "SystemMemoryManager"

    fun init(context: Context) {
        context.applicationContext.registerComponentCallbacks(this)
        Log.i(TAG, "SystemMemoryManager initialized")
    }

    fun register(app: Application) {
        init(app)
    }

    override fun onTrimMemory(level: Int) {

        Log.w(TAG, "onTrimMemory level: $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            MemoryLeakDetector.runSystemGC()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        Log.e(TAG, "onLowMemory triggered")
        MemoryLeakDetector.runSystemGC()
    }
}

class TabMemoryManager(private val maxActiveTabs: Int = 6) {

    private val tabLruList = mutableListOf<String>()

    fun recordTabAccess(tabId: String) {
        synchronized(tabLruList) {
            tabLruList.remove(tabId)
            tabLruList.add(0, tabId) // Prepend as most recently accessed
        }
    }

    fun getTabsToTrim(activeTabs: List<String>): List<String> {
        synchronized(tabLruList) {
            val eligibleToTrim = tabLruList.filter { it in activeTabs }
            if (eligibleToTrim.size > maxActiveTabs) {
                return eligibleToTrim.subList(maxActiveTabs, eligibleToTrim.size)
            }
            return emptyList()
        }
    }

    fun removeTab(tabId: String) {
        synchronized(tabLruList) {
            tabLruList.remove(tabId)
        }
    }

    fun clearAll() {
        synchronized(tabLruList) {
            tabLruList.clear()
        }
    }
}
