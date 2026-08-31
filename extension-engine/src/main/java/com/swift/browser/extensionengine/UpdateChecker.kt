package com.swift.browser.extensionengine

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(
    private val updateManifestParser: UpdateManifestParser = UpdateManifestParser()
) {

    /**
     * Checks if an update is available for an installed extension by fetching its update manifest URL.
     */
    fun checkForUpdate(installedExtension: ParsedExtension, updateUrl: String): ExtensionUpdateInfo? {
        if (updateUrl.isBlank()) return null

        val manifestContent = try {
            fetchUpdateManifest(updateUrl)
        } catch (e: Exception) {
            return null
        }

        val updateInfos = updateManifestParser.parseUpdateManifest(manifestContent)
        val matchingUpdate = updateInfos.firstOrNull {
            it.extensionId.equals(installedExtension.id, ignoreCase = true) || updateInfos.size == 1
        } ?: return null

        if (ZipExtensionInstaller.compareVersions(matchingUpdate.version, installedExtension.version) > 0) {
            return matchingUpdate
        }

        return null
    }

    private fun fetchUpdateManifest(updateUrl: String): String {
        val url = URL(updateUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Failed to fetch update manifest from $updateUrl: ${connection.responseCode}")
        }

        return connection.inputStream.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }
}
