package com.swift.browser.downloadengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DownloadHelper {
    fun extractFileName(disposition: String?, url: String, mimeType: String): String {
        if (!disposition.isNullOrEmpty()) {
            val match = Regex("""filename[^;=\n]*=((['"]).*?\2|[^;\n]*)""").find(disposition)
            if (match != null) {
                return match.groupValues[1].trim('"', '\'', ' ')
            }
        }
        
        val urlPath = android.net.Uri.parse(url).lastPathSegment
        if (!urlPath.isNullOrEmpty() && urlPath.contains(".")) {
            return urlPath
        }
        
        val ext = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    fun triggerFileDownload(
        context: Context,
        engine: DownloadEngine,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        contentLength: Long,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ) {
        val fileName = extractFileName(contentDisposition, url, mimetype)
        CoroutineScope(Dispatchers.Main).launch {
            android.widget.Toast.makeText(
                context,
                "Starting download: $fileName",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        CoroutineScope(Dispatchers.IO).launch {
            engine.startDownload(
                url = url,
                fileName = fileName,
                mimeType = mimetype,
                threads = 8,
                isPrivate = isPrivate,
                privateSessionId = privateSessionId
            )
        }
    }
}
