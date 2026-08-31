package com.swift.browser.downloadengine

import android.content.Context
import android.os.Environment
import java.io.File

data class DownloadConfig(
    val maxConcurrentDownloads: Int = 3,
    val maxThreadsPerDownload: Int = 4, // configurable: 1, 2, 4, 8
    val wifiOnly: Boolean = false,
    val batterySaverMode: Boolean = false,
    val autoResume: Boolean = true
)

object DownloadScheduler {
    
    fun getCategoryForMimeType(mimeType: String): String {
        return when {
            mimeType.startsWith("video/") -> "Videos"
            mimeType.startsWith("audio/") -> "Audio"
            mimeType.startsWith("image/") -> "Images"
            mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("msword") || mimeType.contains("sheet") -> "Documents"
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("tar") || mimeType.contains("compress") -> "Archives"
            else -> "Documents"
        }
    }

    fun getDownloadDirectory(context: Context, category: String): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val swiftDir = File(root, "SwiftBrowserDownload")
        val categoryDir = File(swiftDir, category)
        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }
        return categoryDir
    }

    fun getOutputFile(context: Context, category: String, fileName: String): File {
        val dir = getDownloadDirectory(context, category)
        var file = File(dir, fileName)
        if (!file.exists()) return file
        
        // Resolve duplicates with suffix counter
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "")
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_$counter$suffix")
            counter++
        }
        return file
    }
}
