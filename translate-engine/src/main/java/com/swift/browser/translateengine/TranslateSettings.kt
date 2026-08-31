package com.swift.browser.translateengine

import android.content.Context
import android.content.SharedPreferences

class TranslateSettings(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "swift_translate_settings",
        Context.MODE_PRIVATE
    )

    fun isAutoTranslateEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_TRANSLATE, true)
    }

    fun setAutoTranslateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun getDefaultTargetLanguage(): String {
        return prefs.getString(KEY_DEFAULT_TARGET_LANGUAGE, "hi") ?: "hi"
    }

    fun setDefaultTargetLanguage(langCode: String) {
        prefs.edit().putString(KEY_DEFAULT_TARGET_LANGUAGE, langCode).apply()
    }

    fun isOfferToTranslateEnabled(): Boolean {
        return prefs.getBoolean(KEY_OFFER_TRANSLATE, true)
    }

    fun setOfferToTranslateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFER_TRANSLATE, enabled).apply()
    }

    fun addNeverTranslateSite(host: String) {
        val current = getNeverTranslateSites().toMutableSet()
        current.add(host)
        prefs.edit().putStringSet(KEY_NEVER_TRANSLATE_SITES, current).apply()
    }

    fun removeNeverTranslateSite(host: String) {
        val current = getNeverTranslateSites().toMutableSet()
        current.remove(host)
        prefs.edit().putStringSet(KEY_NEVER_TRANSLATE_SITES, current).apply()
    }

    fun getNeverTranslateSites(): Set<String> {
        return prefs.getStringSet(KEY_NEVER_TRANSLATE_SITES, emptySet()) ?: emptySet()
    }

    fun addAlwaysTranslateSite(host: String) {
        val current = getAlwaysTranslateSites().toMutableSet()
        current.add(host)
        prefs.edit().putStringSet(KEY_ALWAYS_TRANSLATE_SITES, current).apply()
    }

    fun getAlwaysTranslateSites(): Set<String> {
        return prefs.getStringSet(KEY_ALWAYS_TRANSLATE_SITES, emptySet()) ?: emptySet()
    }

    fun addNeverTranslateLanguage(langCode: String) {
        val current = getNeverTranslateLanguages().toMutableSet()
        current.add(langCode)
        prefs.edit().putStringSet(KEY_NEVER_TRANSLATE_LANGUAGES, current).apply()
    }

    fun removeNeverTranslateLanguage(langCode: String) {
        val current = getNeverTranslateLanguages().toMutableSet()
        current.remove(langCode)
        prefs.edit().putStringSet(KEY_NEVER_TRANSLATE_LANGUAGES, current).apply()
    }

    fun getNeverTranslateLanguages(): Set<String> {
        return prefs.getStringSet(KEY_NEVER_TRANSLATE_LANGUAGES, emptySet()) ?: emptySet()
    }

    companion object {
        private const val KEY_AUTO_TRANSLATE = "key_auto_translate"
        private const val KEY_DEFAULT_TARGET_LANGUAGE = "key_default_target_lang"
        private const val KEY_OFFER_TRANSLATE = "key_offer_translate"
        private const val KEY_NEVER_TRANSLATE_SITES = "key_never_translate_sites"
        private const val KEY_ALWAYS_TRANSLATE_SITES = "key_always_translate_sites"
        private const val KEY_NEVER_TRANSLATE_LANGUAGES = "key_never_translate_languages"
    }
}
