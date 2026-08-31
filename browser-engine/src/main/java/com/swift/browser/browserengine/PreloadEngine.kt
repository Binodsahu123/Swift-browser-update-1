package com.swift.browser.browserengine

import android.content.Context
import android.util.Log

private const val TAG = "PreloadEngine"

class PreloadEngine(private val context: Context) {
    private var preloadedScreens = mutableSetOf<String>()

    fun preloadScreen(screenName: String, preloadAction: () -> Unit) {
        if (preloadedScreens.contains(screenName)) return
        try {
            Log.d(TAG, "Preloading configurations & assets for: $screenName")
            preloadAction()
            preloadedScreens.add(screenName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed preloading for $screenName", e)
        }
    }

    fun preloadAll(
        newTabAction: () -> Unit,
        searchAction: () -> Unit,
        bookmarksAction: () -> Unit,
        downloadsAction: () -> Unit,
        settingsAction: () -> Unit
    ) {
        preloadScreen("New Tab Screen", newTabAction)
        preloadScreen("Search Screen", searchAction)
        preloadScreen("Bookmarks Screen", bookmarksAction)
        preloadScreen("Downloads Screen", downloadsAction)
        preloadScreen("Settings Screen", settingsAction)
    }

    fun isScreenPreloaded(screenName: String): Boolean = preloadedScreens.contains(screenName)

    fun clearPreloads() {
        preloadedScreens.clear()
    }
}
