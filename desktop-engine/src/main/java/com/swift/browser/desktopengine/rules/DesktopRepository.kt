package com.swift.browser.desktopengine.rules

import android.content.Context
import android.content.SharedPreferences
import com.swift.browser.desktopengine.api.DesktopDefaultMode
import com.swift.browser.desktopengine.api.DesktopMode
import java.util.concurrent.ConcurrentHashMap

class DesktopRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val siteExceptionsCache = ConcurrentHashMap<String, DesktopMode>()
    private var defaultModeCache: DesktopDefaultMode = DesktopDefaultMode.AUTO

    init {
        performMigrationIfNeeded()
        loadCache()
    }

    private fun performMigrationIfNeeded() {
        val hasMigrated = prefs.getBoolean(KEY_MIGRATED, false)
        if (hasMigrated) return

        val editor = prefs.edit()

        // 1. Migrate from "desktop_mode_sites"
        val legacyPrefs1 = context.getSharedPreferences(LEGACY_PREFS_SITES, Context.MODE_PRIVATE)
        val legacyDefault = legacyPrefs1.getString("desktop_default_mode", null)
        if (legacyDefault != null) {
            editor.putString(KEY_DEFAULT_MODE, legacyDefault)
        }

        legacyPrefs1.all.forEach { (key, value) ->
            if (key.startsWith("site_mode_") && value is String) {
                val host = key.removePrefix("site_mode_")
                val canonical = DesktopHostNormalizer.getCanonicalHost(host)
                editor.putString("$PREFIX_SITE_MODE$canonical", value)
            } else if (key.startsWith("desktop_mode_") && value is Boolean) {
                val rawHost = key.removePrefix("desktop_mode_")
                val canonical = DesktopHostNormalizer.getCanonicalHost(rawHost)
                val mode = if (value) DesktopMode.DESKTOP.name else DesktopMode.MOBILE.name
                editor.putString("$PREFIX_SITE_MODE$canonical", mode)
            } else if (key.startsWith("site_mode_time_") && value is Long) {
                val rawHost = key.removePrefix("site_mode_time_")
                val canonical = DesktopHostNormalizer.getCanonicalHost(rawHost)
                editor.putLong("$PREFIX_SITE_TIME$canonical", value)
            }
        }

        // 2. Migrate from "swift_desktop_compatibility"
        val legacyPrefs2 = context.getSharedPreferences(LEGACY_PREFS_COMPAT, Context.MODE_PRIVATE)
        legacyPrefs2.all.forEach { (key, value) ->
            if (key.startsWith("host_desktop_enabled_") && value is Boolean && value) {
                val host = key.removePrefix("host_desktop_enabled_")
                val canonical = DesktopHostNormalizer.getCanonicalHost(host)
                // Only set if not already present from primary legacy store
                val existingKey = "$PREFIX_SITE_MODE$canonical"
                if (!prefs.contains(existingKey)) {
                    editor.putString(existingKey, DesktopMode.DESKTOP.name)
                    editor.putLong("$PREFIX_SITE_TIME$canonical", System.currentTimeMillis())
                }
            }
        }

        editor.putBoolean(KEY_MIGRATED, true)
        editor.apply()
    }

    private fun loadCache() {
        val savedDefault = prefs.getString(KEY_DEFAULT_MODE, DesktopDefaultMode.AUTO.name)
        defaultModeCache = try {
            DesktopDefaultMode.valueOf(savedDefault ?: DesktopDefaultMode.AUTO.name)
        } catch (_: Exception) {
            DesktopDefaultMode.AUTO
        }

        siteExceptionsCache.clear()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX_SITE_MODE) && value is String) {
                val host = key.removePrefix(PREFIX_SITE_MODE)
                try {
                    siteExceptionsCache[host] = DesktopMode.valueOf(value)
                } catch (_: Exception) {}
            }
        }
    }

    fun getDefaultMode(): DesktopDefaultMode = defaultModeCache

    fun setDefaultMode(mode: DesktopDefaultMode) {
        defaultModeCache = mode
        prefs.edit().putString(KEY_DEFAULT_MODE, mode.name).apply()
    }

    fun getSiteExceptions(): Map<String, DesktopMode> = HashMap(siteExceptionsCache)

    fun getSiteMode(host: String): DesktopMode? {
        val canonical = DesktopHostNormalizer.getCanonicalHost(host)
        return siteExceptionsCache[canonical]
    }

    fun setSiteMode(host: String, mode: DesktopMode) {
        if (host.isEmpty()) return
        val canonical = DesktopHostNormalizer.getCanonicalHost(host)
        siteExceptionsCache[canonical] = mode

        prefs.edit()
            .putString("$PREFIX_SITE_MODE$canonical", mode.name)
            .putLong("$PREFIX_SITE_TIME$canonical", System.currentTimeMillis())
            .apply()
    }

    fun removeSiteMode(host: String) {
        val canonical = DesktopHostNormalizer.getCanonicalHost(host)
        siteExceptionsCache.remove(canonical)
        prefs.edit()
            .remove("$PREFIX_SITE_MODE$canonical")
            .remove("$PREFIX_SITE_TIME$canonical")
            .apply()
    }

    fun getSiteTimestamp(host: String): Long {
        val canonical = DesktopHostNormalizer.getCanonicalHost(host)
        return prefs.getLong("$PREFIX_SITE_TIME$canonical", 0L)
    }

    fun clearAllSiteExceptions() {
        val keysToRemove = prefs.all.keys.filter { it.startsWith(PREFIX_SITE_MODE) || it.startsWith(PREFIX_SITE_TIME) }
        val editor = prefs.edit()
        keysToRemove.forEach { editor.remove(it) }
        editor.apply()
        siteExceptionsCache.clear()
    }

    companion object {
        private const val PREFS_NAME = "swift_desktop_repository"
        private const val LEGACY_PREFS_SITES = "desktop_mode_sites"
        private const val LEGACY_PREFS_COMPAT = "swift_desktop_compatibility"

        private const val KEY_MIGRATED = "has_migrated_v1"
        private const val KEY_DEFAULT_MODE = "desktop_default_mode"
        private const val PREFIX_SITE_MODE = "site_mode_"
        private const val PREFIX_SITE_TIME = "site_mode_time_"
    }
}
