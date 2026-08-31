package com.swift.browser.webstudio.engine

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

class ProjectLoader(private val context: Context, private val diagnosticsManager: DiagnosticsManager) {
    fun importZip(uri: Uri): File? {
        try {
            val tempDir = File(context.cacheDir, "webstudio_zip_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            newFile.outputStream().use { output ->
                                zis.copyTo(output)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            diagnosticsManager.logEvent("Project extracted to ${tempDir.absolutePath}")
            return tempDir
        } catch (e: Exception) {
            diagnosticsManager.logError("Failed to extract ZIP project", e)
            return null
        }
    }
}
