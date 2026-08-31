package com.swift.browser.extensionengine

import org.json.JSONObject
import org.json.JSONArray

enum class ApiSupportLevel {
    FULL,
    PARTIAL,
    PRIVILEGED,
    UNSUPPORTED
}

data class ApiCapabilityEntry(
    val apiName: String,
    val permissionRequired: String?,
    val supportLevel: ApiSupportLevel,
    val documentation: String,
    val isPrivateSessionAware: Boolean = true
)

object ExtensionAdvancedApiCapabilityMatrix {
    private val matrix = listOf(
        ApiCapabilityEntry("alarms", "alarms", ApiSupportLevel.FULL, "Standard MV3 alarm execution via Handler and ServiceWorkerEventDispatcher."),
        ApiCapabilityEntry("management", "management", ApiSupportLevel.FULL, "Querying, state modification, and uninstallation of extensions via ExtensionRegistry."),
        ApiCapabilityEntry("idle", "idle", ApiSupportLevel.FULL, "Real system state detection using PowerManager and KeyguardManager."),
        ApiCapabilityEntry("i18n", null, ApiSupportLevel.FULL, "Complete locale resolution and localization support using manifest messages.json."),
        ApiCapabilityEntry("search", "search", ApiSupportLevel.FULL, "Formulate search URLs and route search queries via BrowserDelegate."),
        ApiCapabilityEntry("topSites", "topSites", ApiSupportLevel.FULL, "Retrieves top site lists maintained via BrowserRepository."),
        ApiCapabilityEntry("tts", "tts", ApiSupportLevel.FULL, "Synthesizes text-to-speech queries via Android TextToSpeech engine."),
        ApiCapabilityEntry("system.cpu", "system.cpu", ApiSupportLevel.FULL, "Provides real-time CPU characteristics from Android system stats."),
        ApiCapabilityEntry("system.memory", "system.memory", ApiSupportLevel.FULL, "Provides system memory stats (RAM capacity and availability) via ActivityManager."),
        ApiCapabilityEntry("system.storage", "system.storage", ApiSupportLevel.FULL, "Provides local filesystem capacity and availability stats via StatFs.")
    )

    fun getCapabilities(): List<ApiCapabilityEntry> = matrix

    fun getAsJson(): JSONObject {
        val root = JSONObject()
        val array = JSONArray()
        for (entry in matrix) {
            val item = JSONObject().apply {
                put("apiName", entry.apiName)
                put("permissionRequired", entry.permissionRequired ?: JSONObject.NULL)
                put("supportLevel", entry.supportLevel.name)
                put("documentation", entry.documentation)
                put("isPrivateSessionAware", entry.isPrivateSessionAware)
            }
            array.put(item)
        }
        root.put("apis", array)
        return root
    }

    fun isSupported(apiName: String): Boolean {
        val normalized = apiName.lowercase().trim()
        return matrix.any { entry ->
            normalized.startsWith(entry.apiName.lowercase()) && entry.supportLevel != ApiSupportLevel.UNSUPPORTED
        }
    }
}
