package com.swift.browser.databasecore

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("swift_browser_prefs", Context.MODE_PRIVATE)

    private val _isJavaScriptEnabled = MutableStateFlow(prefs.getBoolean("js_enabled", true))
    val isJavaScriptEnabled: StateFlow<Boolean> = _isJavaScriptEnabled

    private val _isHardwareAccelerationEnabled = MutableStateFlow(prefs.getBoolean("hardware_acc_enabled", true))
    val isHardwareAccelerationEnabled: StateFlow<Boolean> = _isHardwareAccelerationEnabled

    private val _newTabWallpaper = MutableStateFlow(prefs.getString("new_tab_wallpaper", "Frosted Glass") ?: "Frosted Glass")
    val newTabWallpaper: StateFlow<String> = _newTabWallpaper

    private val _readerFontSize = MutableStateFlow(prefs.getInt("reader_font_size", 16))
    val readerFontSize: StateFlow<Int> = _readerFontSize

    private val _appTheme = MutableStateFlow(prefs.getString("app_theme_mode", "System") ?: "System")
    val appTheme: StateFlow<String> = _appTheme

    private val _isPurgePrivateOnTimeoutOrExit = MutableStateFlow(prefs.getBoolean("purge_private_on_timeout_exit", true))
    val isPurgePrivateOnTimeoutOrExit: StateFlow<Boolean> = _isPurgePrivateOnTimeoutOrExit

    private val _biometricTimeoutSeconds = MutableStateFlow(prefs.getInt("biometric_timeout_seconds", 0))
    val biometricTimeoutSeconds: StateFlow<Int> = _biometricTimeoutSeconds

    fun setJavaScriptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("js_enabled", enabled).apply()
        _isJavaScriptEnabled.value = enabled
    }

    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hardware_acc_enabled", enabled).apply()
        _isHardwareAccelerationEnabled.value = enabled
    }

    fun setNewTabWallpaper(wallpaper: String) {
        prefs.edit().putString("new_tab_wallpaper", wallpaper).apply()
        _newTabWallpaper.value = wallpaper
    }

    fun setAppTheme(theme: String) {
        prefs.edit().putString("app_theme_mode", theme).apply()
        _appTheme.value = theme
    }

    fun setReaderFontSize(size: Int) {
        prefs.edit().putInt("reader_font_size", size).apply()
        _readerFontSize.value = size
    }

    fun setPurgePrivateOnTimeoutOrExit(enabled: Boolean) {
        prefs.edit().putBoolean("purge_private_on_timeout_exit", enabled).apply()
        _isPurgePrivateOnTimeoutOrExit.value = enabled
    }

    fun setBiometricTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt("biometric_timeout_seconds", seconds).apply()
        _biometricTimeoutSeconds.value = seconds
    }

    // Generic preferences methods for advanced settings UI
    fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getAllKeysWithPrefix(prefix: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val allEntries = prefs.all
            for ((key, value) in allEntries) {
                if (key.startsWith(prefix) && value is String && value.isNotEmpty()) {
                    val actualKey = key.substring(prefix.length)
                    result.add(actualKey to value)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
