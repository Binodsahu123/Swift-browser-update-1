package com.swift.browser.translateengine

import android.content.Context
import android.content.SharedPreferences

object TranslationPreferenceManager {
    private const val PREFS_NAME = "swift_translation_preferences"
    private const val KEY_TARGET_LANG_CODE = "pref_target_lang_code"
    private const val KEY_TARGET_LANG_NAME = "pref_target_lang_name"
    private const val KEY_AUTO_TRANSLATE = "pref_auto_translate"
    private const val KEY_ACTIVE_DOMAINS = "pref_active_domains"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTargetLanguageCode(context: Context): String {
        // Default target language: "en" for English (as per rule: "Default target language: English")
        return getPrefs(context).getString(KEY_TARGET_LANG_CODE, "en") ?: "en"
    }

    fun getTargetLanguageName(context: Context): String {
        return getPrefs(context).getString(KEY_TARGET_LANG_NAME, "English") ?: "English"
    }

    fun saveTargetLanguage(context: Context, langCode: String, langName: String) {
        getPrefs(context).edit()
            .putString(KEY_TARGET_LANG_CODE, langCode)
            .putString(KEY_TARGET_LANG_NAME, langName)
            .apply()
    }

    fun isAutoTranslateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_TRANSLATE, true)
    }

    fun setAutoTranslateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun getPersistedActiveDomains(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_ACTIVE_DOMAINS, emptySet()) ?: emptySet()
    }

    fun addPersistedActiveDomain(context: Context, domain: String) {
        if (domain.isEmpty()) return
        val current = getPersistedActiveDomains(context).toMutableSet()
        current.add(domain)
        getPrefs(context).edit().putStringSet(KEY_ACTIVE_DOMAINS, current).apply()
    }

    fun removePersistedActiveDomain(context: Context, domain: String) {
        if (domain.isEmpty()) return
        val current = getPersistedActiveDomains(context).toMutableSet()
        current.remove(domain)
        getPrefs(context).edit().putStringSet(KEY_ACTIVE_DOMAINS, current).apply()
    }
}
