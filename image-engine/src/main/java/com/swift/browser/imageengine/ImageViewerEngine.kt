package com.swift.browser.imageengine

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat

data class ImageMetadata(
    val format: String,
    val resolution: String,
    val size: String,
    val url: String
)

object ImageMetadataManager {
    fun resolveMetadata(context: Context, filePath: String, originalUrl: String): ImageMetadata {
        val file = File(filePath)
        if (!file.exists()) {
            return ImageMetadata("UNKNOWN", "0 x 0", "0 KB", originalUrl)
        }

        // Resolve format
        val extension = file.extension.uppercase()
        val format = if (extension.isNotBlank()) extension else "PNG"

        // Resolve resolution without loading full bitmap
        var resolution = "Unknown"
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                resolution = "${options.outWidth} x ${options.outHeight}"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Resolve size
        val bytes = file.length()
        val size = formatFileSize(bytes)

        return ImageMetadata(
            format = format,
            resolution = resolution,
            size = size,
            url = originalUrl.ifBlank { "local://${file.name}" }
        )
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 Bytes"
        val units = arrayOf("Bytes", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}

object ImageCacheManager {
    fun saveImageAs(context: Context, sourceFilePath: String, destinationName: String, onResult: (Boolean, String) -> Unit) {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) {
                onResult(false, "Source file does not exist")
                return
            }

            // Save to Public Downloads Folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            var targetName = destinationName
            if (!targetName.contains(".")) {
                targetName += "." + sourceFile.extension
            }
            val destFile = File(downloadsDir, targetName)

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            onResult(true, "Saved to ${destFile.absolutePath}")
        } catch (e: Exception) {
            onResult(false, "Failed to save: ${e.localizedMessage}")
        }
    }

    fun copyImageToClipboard(context: Context, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            
            // Generate content URI using FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val clip = android.content.ClipData.newUri(context.contentResolver, "Image", uri)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getShareIntent(context: Context, filePath: String): android.content.Intent? {
        val file = File(filePath)
        if (!file.exists()) return null

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

class ImageViewerEngine {
    // Engine operations and utilities
}
