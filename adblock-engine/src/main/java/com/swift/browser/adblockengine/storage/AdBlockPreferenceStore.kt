package com.swift.browser.adblockengine.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Handles fast key-value preferences for rapid toggle lookup.
 */
object AdBlockPreferenceStore {
    private const val PREFS_NAME = "swift_browser_adblock_prefs"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            init(context)
        }
        return prefs!!
    }

    fun saveBoolean(context: Context, key: String, value: Boolean) {
        getPrefs(context).edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return getPrefs(context).getBoolean(key, defaultValue)
    }

    fun saveString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, defaultValue: String?): String? {
        return getPrefs(context).getString(key, defaultValue)
    }

    fun saveLong(context: Context, key: String, value: Long) {
        getPrefs(context).edit().putLong(key, value).apply()
    }

    fun getLong(context: Context, key: String, defaultValue: Long): Long {
        return getPrefs(context).getLong(key, defaultValue)
    }

    fun saveInt(context: Context, key: String, value: Int) {
        getPrefs(context).edit().putInt(key, value).apply()
    }

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return getPrefs(context).getInt(key, defaultValue)
    }

    fun saveStringSet(context: Context, key: String, values: Set<String>) {
        getPrefs(context).edit().putStringSet(key, values).apply()
    }

    fun getStringSet(context: Context, key: String): Set<String>? {
        return getPrefs(context).getStringSet(key, null)
    }
}
