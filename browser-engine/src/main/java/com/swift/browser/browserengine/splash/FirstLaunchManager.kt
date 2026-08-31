package com.swift.browser.browserengine.splash

import android.content.Context

class FirstLaunchManager(context: Context) {
    private val prefs = context.getSharedPreferences("swift_first_launch_prefs", Context.MODE_PRIVATE)

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean("is_first_launch", true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    fun completeOnboarding() {
        setFirstLaunchCompleted()
    }

    fun markAiScreenCompleted() {
        prefs.edit().putBoolean("ai_completed", true).apply()
    }

    fun markNotificationCompleted() {
        prefs.edit().putBoolean("notification_completed", true).apply()
    }

    fun markFilePermissionCompleted() {
        prefs.edit().putBoolean("file_permission_completed", true).apply()
    }

    fun markPrivacyAccepted() {
        prefs.edit().putBoolean("privacy_accepted", true).apply()
    }

    fun getSelectedTheme(): String {
        return prefs.getString("selected_theme", "System") ?: "System"
    }

    fun saveSelectedTheme(theme: String) {
        prefs.edit().putString("selected_theme", theme).apply()
    }
}

class MediaPermissionEngine(private val context: Context) {
    fun markFilePermissionGranted() {
        val prefs = context.getSharedPreferences("media_permission_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("granted", true).apply()
    }
}
