package com.swift.browser.extensionengine

import org.json.JSONObject

data class ExtensionUpdateInfo(
    val extensionId: String,
    val version: String,
    val codebaseUrl: String
)

class UpdateManifestParser(
    private val delegate: ExtensionUpdateManifestParser = ExtensionUpdateManifestParser()
) {

    /**
     * Parses Omaha XML or Chromium JSON update manifest.
     */
    fun parseUpdateManifest(manifestContent: String): List<ExtensionUpdateInfo> {
        return delegate.parseUpdateManifest(manifestContent)
    }
}
