package com.swift.browser.extensionengine

import com.swift.browser.extensionengine.origin.ExtensionUrl
import java.io.File

class PopupManager {

    /**
     * Resolves the canonical chrome-extension:// URL to launch an extension action popup.
     */
    fun getPopupUrl(context: android.content.Context, extensionId: String, defaultPopupPath: String): String? {
        if (defaultPopupPath.isBlank()) return null
        val cleanPath = defaultPopupPath.removePrefix("/").removePrefix("./")
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
        val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, cleanPath)
        if (targetFile != null && targetFile.exists() && targetFile.isFile) {
            val relPath = targetFile.relativeTo(extensionDir).path.replace('\\', '/')
            return ExtensionUrl.toExtensionUrl(extensionId, relPath)
        }
        return ExtensionUrl.toExtensionUrl(extensionId, cleanPath)
    }
}
