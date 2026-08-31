package com.swift.browser.webstudio.engine

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileExplorerEngine(private val diagnosticsManager: DiagnosticsManager) {
    fun loadDirectory(dir: File?, onLoaded: (List<File>) -> Unit) {
        if (dir != null && dir.exists() && dir.isDirectory) {
            try {
                val files = dir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
                diagnosticsManager.logEvent("Loaded directory: ${dir.name} with ${files.size} items")
                onLoaded(files)
            } catch (e: Exception) {
                diagnosticsManager.logError("Error loading directory ${dir.name}", e)
                onLoaded(emptyList())
            }
        } else {
            diagnosticsManager.logEvent("Attempted to load invalid or empty directory")
            onLoaded(emptyList())
        }
    }
}
