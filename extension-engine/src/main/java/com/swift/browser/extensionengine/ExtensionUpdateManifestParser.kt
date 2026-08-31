package com.swift.browser.extensionengine

import org.json.JSONObject

/**
 * Production-grade parser for Chromium/Omaha Extension Update Manifests (XML & JSON).
 */
class ExtensionUpdateManifestParser {

    /**
     * Parses an update manifest payload (XML or JSON) and returns valid update descriptors.
     * Enforces HTTPS URL scheme and version string validation.
     */
    fun parseUpdateManifest(manifestContent: String): List<ExtensionUpdateInfo> {
        val trimmed = manifestContent.trim()
        if (trimmed.startsWith("{")) {
            return parseJsonUpdateManifest(trimmed)
        } else if (trimmed.startsWith("<") || trimmed.contains("<gupdate") || trimmed.contains("<app")) {
            return parseXmlUpdateManifest(trimmed)
        }
        return emptyList()
    }

    private fun parseJsonUpdateManifest(jsonStr: String): List<ExtensionUpdateInfo> {
        val results = mutableListOf<ExtensionUpdateInfo>()
        try {
            val root = JSONObject(jsonStr)
            val apps = root.optJSONArray("apps")
            if (apps != null) {
                for (i in 0 until apps.length()) {
                    val app = apps.optJSONObject(i) ?: continue
                    val appid = app.optString("appid", "").ifBlank { app.optString("id", "") }.trim().lowercase()
                    val updateCheck = app.optJSONObject("updatecheck") ?: app
                    val version = updateCheck.optString("version", "").trim()
                    val codebase = updateCheck.optString("codebase", "").ifBlank { updateCheck.optString("url", "") }.trim()

                    if (isValidUpdateEntry(appid, version, codebase)) {
                        results.add(ExtensionUpdateInfo(appid, version, codebase))
                    }
                }
            } else if (root.has("version") && (root.has("codebase") || root.has("url"))) {
                val appid = root.optString("id", "").ifBlank { root.optString("appid", "") }.trim().lowercase()
                val version = root.optString("version", "").trim()
                val codebase = root.optString("codebase", "").ifBlank { root.optString("url", "") }.trim()

                if (isValidUpdateEntry(appid, version, codebase)) {
                    results.add(ExtensionUpdateInfo(appid, version, codebase))
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return results
    }

    private fun parseXmlUpdateManifest(xmlStr: String): List<ExtensionUpdateInfo> {
        val results = mutableListOf<ExtensionUpdateInfo>()
        try {
            // XML attribute matcher regex for Omaha update response
            val appRegex = Regex("<app\\s+[^>]*appid=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
            val updateCheckRegex = Regex("<updatecheck\\s+[^>]*codebase=[\"']([^\"']+)[\"']\\s+version=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
            val updateCheckAltRegex = Regex("<updatecheck\\s+[^>]*version=[\"']([^\"']+)[\"']\\s+codebase=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)

            val appMatches = appRegex.findAll(xmlStr)
            for (match in appMatches) {
                val appid = match.groupValues[1].trim().lowercase()
                val appStart = match.range.first
                val appBlock = xmlStr.substring(appStart, minOf(xmlStr.length, appStart + 1500))

                var version = ""
                var codebase = ""

                val ucMatch = updateCheckRegex.find(appBlock)
                if (ucMatch != null) {
                    codebase = ucMatch.groupValues[1].trim()
                    version = ucMatch.groupValues[2].trim()
                } else {
                    val ucAltMatch = updateCheckAltRegex.find(appBlock)
                    if (ucAltMatch != null) {
                        version = ucAltMatch.groupValues[1].trim()
                        codebase = ucAltMatch.groupValues[2].trim()
                    }
                }

                if (isValidUpdateEntry(appid, version, codebase)) {
                    results.add(ExtensionUpdateInfo(appid, version, codebase))
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return results
    }

    private fun isValidUpdateEntry(appid: String, version: String, codebase: String): Boolean {
        if (appid.isBlank() || version.isBlank() || codebase.isBlank()) return false

        // Version string validation
        if (!ExtensionVersionComparator.isValidVersion(version)) return false

        // Enforce HTTPS security on remote codebase URLs (allow local debug HTTP)
        val isHttps = codebase.startsWith("https://", ignoreCase = true)
        val isLocalDebug = codebase.startsWith("http://localhost", ignoreCase = true) ||
                codebase.startsWith("http://127.0.0.1", ignoreCase = true) ||
                codebase.startsWith("file://", ignoreCase = true)

        return isHttps || isLocalDebug
    }
}
