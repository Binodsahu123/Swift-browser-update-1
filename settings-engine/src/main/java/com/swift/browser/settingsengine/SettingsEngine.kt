package com.swift.browser.settingsengine

import android.content.Context
import com.swift.browser.databasecore.PreferenceManager
import kotlinx.coroutines.flow.StateFlow

interface SettingsEngine {
    fun getPreference(key: String, defaultValue: String): String
    fun setPreference(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun setInt(key: String, value: Int)

    // Core Browser UI & System Preferences
    var isJavaScriptEnabled: Boolean
    var isHardwareAccelerationEnabled: Boolean
    var newTabWallpaper: String
    var addressBarPosition: String
    var showHomeButton: Boolean
    var defaultSearchEngine: String
    var homepageType: String
    var homepageCustomUrl: String
    var readerFontSize: Int
    var appTheme: String

    // Privacy & Security Preferences
    var isHttpsOnlyMode: Boolean
    var isSafeBrowsingEnabled: Boolean
    var isDoNotTrackEnabled: Boolean
    var isBlockThirdPartyCookies: Boolean
    var isCanvasFingerprintProtected: Boolean
    var isWebRtcLeakProtected: Boolean
    var isClearDataOnExit: Boolean
    var isPurgePrivateOnTimeoutOrExit: Boolean
    var biometricTimeoutSeconds: Int

    // AdBlock & Content Filtering Preferences
    var isAdBlockEnabled: Boolean
    var isBlockTrackers: Boolean
    var isBlockPopups: Boolean
    var isAutoUpdateFilterLists: Boolean

    // Downloads Preferences
    var isDownloadWifiOnly: Boolean
    var isAskBeforeDownload: Boolean
    var isMultithreadedDownload: Boolean
    var isDownloadAlertsEnabled: Boolean
    var downloadDirectory: String

    // Media & Web Preferences
    var isDomStorageEnabled: Boolean
    var isBackgroundAudioEnabled: Boolean
    var isAutoPipEnabled: Boolean
    var isMediaSnifferEnabled: Boolean

    // Translation & AI Preferences
    var targetLanguage: String
    var isAutoTranslateEnabled: Boolean
    var isTranslatePromptEnabled: Boolean
    var isAiAssistantEnabled: Boolean
    var isAiAutoSummarize: Boolean
    var isAiContextAware: Boolean

    fun resetToDefaults()
}

class SettingsEngineImpl(private val context: Context) : SettingsEngine {
    private val prefManager = PreferenceManager(context)

    override fun getPreference(key: String, defaultValue: String): String {
        return prefManager.getString(key, defaultValue)
    }

    override fun setPreference(key: String, value: String) {
        prefManager.setString(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefManager.getBoolean(key, defaultValue)
    }

    override fun setBoolean(key: String, value: Boolean) {
        prefManager.setBoolean(key, value)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefManager.getInt(key, defaultValue)
    }

    override fun setInt(key: String, value: Int) {
        prefManager.setInt(key, value)
    }

    // Core Browser UI & System Preferences
    override var isJavaScriptEnabled: Boolean
        get() = prefManager.isJavaScriptEnabled.value
        set(value) { prefManager.setJavaScriptEnabled(value) }

    override var isHardwareAccelerationEnabled: Boolean
        get() = prefManager.isHardwareAccelerationEnabled.value
        set(value) { prefManager.setHardwareAccelerationEnabled(value) }

    override var newTabWallpaper: String
        get() = prefManager.newTabWallpaper.value
        set(value) { prefManager.setNewTabWallpaper(value) }

    override var addressBarPosition: String
        get() = prefManager.getString("address_bar_position", "top")
        set(value) { prefManager.setString("address_bar_position", value) }

    override var showHomeButton: Boolean
        get() = prefManager.getBoolean("show_home_button", true)
        set(value) { prefManager.setBoolean("show_home_button", value) }

    override var defaultSearchEngine: String
        get() = prefManager.getString("default_search_engine", "Google")
        set(value) { prefManager.setString("default_search_engine", value) }

    override var homepageType: String
        get() = prefManager.getString("homepage_type", "ntp")
        set(value) { prefManager.setString("homepage_type", value) }

    override var homepageCustomUrl: String
        get() = prefManager.getString("homepage_custom_url", "https://www.google.com")
        set(value) { prefManager.setString("homepage_custom_url", value) }

    override var readerFontSize: Int
        get() = prefManager.readerFontSize.value
        set(value) { prefManager.setReaderFontSize(value) }

    override var appTheme: String
        get() = prefManager.appTheme.value
        set(value) { prefManager.setAppTheme(value) }

    // Privacy & Security Preferences
    override var isHttpsOnlyMode: Boolean
        get() = prefManager.getBoolean("sec_https_only", false)
        set(value) { prefManager.setBoolean("sec_https_only", value) }

    override var isSafeBrowsingEnabled: Boolean
        get() = prefManager.getBoolean("sec_safe_browsing", true)
        set(value) { prefManager.setBoolean("sec_safe_browsing", value) }

    override var isDoNotTrackEnabled: Boolean
        get() = prefManager.getBoolean("browser_dnt", true)
        set(value) { prefManager.setBoolean("browser_dnt", value) }

    override var isBlockThirdPartyCookies: Boolean
        get() = prefManager.getBoolean("browser_3p_cookies", false)
        set(value) { prefManager.setBoolean("browser_3p_cookies", value) }

    override var isCanvasFingerprintProtected: Boolean
        get() = prefManager.getBoolean("ps_canvas_fingerprint", true)
        set(value) { prefManager.setBoolean("ps_canvas_fingerprint", value) }

    override var isWebRtcLeakProtected: Boolean
        get() = prefManager.getBoolean("ps_webrtc_leak", true)
        set(value) { prefManager.setBoolean("ps_webrtc_leak", value) }

    override var isClearDataOnExit: Boolean
        get() = prefManager.getBoolean("clear_data_on_exit", false)
        set(value) { prefManager.setBoolean("clear_data_on_exit", value) }

    override var isPurgePrivateOnTimeoutOrExit: Boolean
        get() = prefManager.getBoolean("purge_private_on_timeout_exit", true)
        set(value) { prefManager.setPurgePrivateOnTimeoutOrExit(value) }

    override var biometricTimeoutSeconds: Int
        get() = prefManager.getInt("biometric_timeout_seconds", 0)
        set(value) { prefManager.setBiometricTimeoutSeconds(value) }

    // AdBlock & Content Filtering Preferences
    override var isAdBlockEnabled: Boolean
        get() = prefManager.getBoolean("adblock_engine_enabled", true)
        set(value) { prefManager.setBoolean("adblock_engine_enabled", value) }

    override var isBlockTrackers: Boolean
        get() = prefManager.getBoolean("adblock_trackers", true)
        set(value) { prefManager.setBoolean("adblock_trackers", value) }

    override var isBlockPopups: Boolean
        get() = prefManager.getBoolean("adblock_popups", true)
        set(value) { prefManager.setBoolean("adblock_popups", value) }

    override var isAutoUpdateFilterLists: Boolean
        get() = prefManager.getBoolean("adblock_auto_update", true)
        set(value) { prefManager.setBoolean("adblock_auto_update", value) }

    // Downloads Preferences
    override var isDownloadWifiOnly: Boolean
        get() = prefManager.getBoolean("dl_wifi", false)
        set(value) { prefManager.setBoolean("dl_wifi", value) }

    override var isAskBeforeDownload: Boolean
        get() = prefManager.getBoolean("ask_before_download", false)
        set(value) { prefManager.setBoolean("ask_before_download", value) }

    override var isMultithreadedDownload: Boolean
        get() = prefManager.getBoolean("dl_multithread", true)
        set(value) { prefManager.setBoolean("dl_multithread", value) }

    override var isDownloadAlertsEnabled: Boolean
        get() = prefManager.getBoolean("notif_downloads", true)
        set(value) { prefManager.setBoolean("notif_downloads", value) }

    override var downloadDirectory: String
        get() = prefManager.getString("download_directory", "Default (Downloads/SwiftBrowser)")
        set(value) { prefManager.setString("download_directory", value) }

    // Media & Web Preferences
    override var isDomStorageEnabled: Boolean
        get() = prefManager.getBoolean("browser_dom", true)
        set(value) { prefManager.setBoolean("browser_dom", value) }

    override var isBackgroundAudioEnabled: Boolean
        get() = prefManager.getBoolean("audio_background", true)
        set(value) { prefManager.setBoolean("audio_background", value) }

    override var isAutoPipEnabled: Boolean
        get() = prefManager.getBoolean("vid_pip", true)
        set(value) { prefManager.setBoolean("vid_pip", value) }

    override var isMediaSnifferEnabled: Boolean
        get() = prefManager.getBoolean("md_video", true)
        set(value) { prefManager.setBoolean("md_video", value) }

    // Translation & AI Preferences
    override var targetLanguage: String
        get() = prefManager.getString("target_translate_language", "English")
        set(value) { prefManager.setString("target_translate_language", value) }

    override var isAutoTranslateEnabled: Boolean
        get() = prefManager.getBoolean("trans_auto", false)
        set(value) { prefManager.setBoolean("trans_auto", value) }

    override var isTranslatePromptEnabled: Boolean
        get() = prefManager.getBoolean("trans_prompt", true)
        set(value) { prefManager.setBoolean("trans_prompt", value) }

    override var isAiAssistantEnabled: Boolean
        get() = prefManager.getBoolean("ai_assistant", true)
        set(value) { prefManager.setBoolean("ai_assistant", value) }

    override var isAiAutoSummarize: Boolean
        get() = prefManager.getBoolean("ai_summarize", false)
        set(value) { prefManager.setBoolean("ai_summarize", value) }

    override var isAiContextAware: Boolean
        get() = prefManager.getBoolean("ai_context", true)
        set(value) { prefManager.setBoolean("ai_context", value) }

    override fun resetToDefaults() {
        isJavaScriptEnabled = true
        isHardwareAccelerationEnabled = true
        newTabWallpaper = "Frosted Glass"
        addressBarPosition = "top"
        showHomeButton = true
        defaultSearchEngine = "Google"
        homepageType = "ntp"
        homepageCustomUrl = "https://www.google.com"
        readerFontSize = 16
        appTheme = "System"

        isHttpsOnlyMode = false
        isSafeBrowsingEnabled = true
        isDoNotTrackEnabled = true
        isBlockThirdPartyCookies = false
        isCanvasFingerprintProtected = true
        isWebRtcLeakProtected = true
        isClearDataOnExit = false
        isPurgePrivateOnTimeoutOrExit = true
        biometricTimeoutSeconds = 0

        isAdBlockEnabled = true
        isBlockTrackers = true
        isBlockPopups = true
        isAutoUpdateFilterLists = true

        isDownloadWifiOnly = false
        isAskBeforeDownload = false
        isMultithreadedDownload = true
        isDownloadAlertsEnabled = true
        downloadDirectory = "Default (Downloads/SwiftBrowser)"

        isDomStorageEnabled = true
        isBackgroundAudioEnabled = true
        isAutoPipEnabled = true
        isMediaSnifferEnabled = true

        targetLanguage = "English"
        isAutoTranslateEnabled = false
        isTranslatePromptEnabled = true
        isAiAssistantEnabled = true
        isAiAutoSummarize = false
        isAiContextAware = true
    }
}
