package com.swift.browser.extensionengine

import android.content.Context
import com.swift.browser.downloadengine.DownloadEngine
import com.swift.browser.downloadengine.DownloadEngineProvider
import com.swift.browser.downloadengine.DownloadItem
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * ExtensionDownloadsAdapter bridges chrome.downloads.* calls to Orion's download-engine.
 */
class ExtensionDownloadsAdapter(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    private val downloadEngine: DownloadEngine by lazy { DownloadEngineProvider.getEngine(context) }

    private fun verifyDownloadsPermission(sender: ExtensionSender) {
        val ext = registry.getExtension(sender.extensionId)
            ?: throw SecurityException("SecurityError: Extension ${sender.extensionId} not found.")
        if (!permissionManager.hasApiPermission(sender.extensionId, ext.permissions, "downloads")) {
            throw SecurityException("SecurityError: Extension does not have 'downloads' permission in manifest.")
        }
    }

    private fun mapStatusToChromeState(status: String): String {
        return when (status.uppercase()) {
            "COMPLETED" -> "complete"
            "RUNNING", "PENDING", "SCHEDULED" -> "in_progress"
            "PAUSED" -> "in_progress"
            "CANCELLED", "FAILED" -> "interrupted"
            else -> "in_progress"
        }
    }

    private fun formatDownloadItem(item: DownloadItem): JSONObject {
        val isSafe = try {
            com.swift.browser.securityengine.SecurityEngineProvider.api.analyzeDownloadSafety(item.url, null, item.mimeType)
        } catch (e: Exception) {
            true
        }
        val dangerState = if (isSafe) "safe" else "dangerous"

        return JSONObject().apply {
            put("id", item.id)
            put("url", item.url)
            put("filename", if (item.filePath.isNotBlank()) item.filePath else item.title)
            put("danger", dangerState)
            put("mime", item.mimeType)
            put("startTime", item.timestamp)
            put("bytesReceived", item.downloadedSize)
            put("totalBytes", item.totalSize)
            put("state", mapStatusToChromeState(item.status))
            put("paused", item.status.equals("PAUSED", ignoreCase = true))
            put("error", if (item.status.equals("FAILED", ignoreCase = true)) "SERVER_FAILED" else JSONObject.NULL)
            put("isPrivate", item.isPrivate)
        }
    }

    suspend fun download(sender: ExtensionSender, options: JSONObject): Long {
        verifyDownloadsPermission(sender)

        val url = options.optString("url", "")
        if (url.isBlank()) throw IllegalArgumentException("Download URL cannot be blank.")

        val filename = options.optString("filename", "download_${System.currentTimeMillis()}")
        val isPrivate = sender.isPrivate
        val privateSessionId = if (isPrivate) sender.windowId ?: "default_private" else null

        val downloadId = downloadEngine.startDownload(
            url = url,
            fileName = filename,
            mimeType = "application/octet-stream",
            threads = 4,
            isPrivate = isPrivate,
            privateSessionId = privateSessionId
        )

        val itemObj = JSONObject().apply {
            put("id", downloadId)
            put("url", url)
            put("filename", filename)
            put("state", "in_progress")
            put("isPrivate", isPrivate)
        }

        eventManager.triggerEvent("downloads.onCreated", itemObj)

        return downloadId
    }

    suspend fun search(sender: ExtensionSender, query: JSONObject): JSONArray {
        verifyDownloadsPermission(sender)

        val allowPrivate = permissionManager.isAllowedInPrivate(sender.extensionId)
        val allDownloads = downloadEngine.getDownloadsFlow().first()

        var filtered = allDownloads.filter { item ->
            if (item.isPrivate && !allowPrivate) {
                false
            } else if (query.has("id") && query.getLong("id") != item.id) {
                false
            } else if (query.has("url") && !item.url.contains(query.getString("url"), ignoreCase = true)) {
                false
            } else if (query.has("filename") && !item.filePath.contains(query.getString("filename"), ignoreCase = true) && !item.title.contains(query.getString("filename"), ignoreCase = true)) {
                false
            } else if (query.has("query") && !item.title.contains(query.getString("query"), ignoreCase = true) && !item.url.contains(query.getString("query"), ignoreCase = true)) {
                false
            } else if (query.has("mime") && !item.mimeType.contains(query.getString("mime"), ignoreCase = true)) {
                false
            } else if (query.has("state") && mapStatusToChromeState(item.status) != query.getString("state")) {
                false
            } else if (query.has("paused") && item.status.equals("PAUSED", ignoreCase = true) != query.getBoolean("paused")) {
                false
            } else {
                true
            }
        }

        if (query.has("orderBy")) {
            val orderBy = query.getJSONArray("orderBy")
            for (i in 0 until orderBy.length()) {
                val field = orderBy.getString(i)
                filtered = when (field) {
                    "id", "-id" -> if (field.startsWith("-")) filtered.sortedByDescending { it.id } else filtered.sortedBy { it.id }
                    "startTime", "-startTime" -> if (field.startsWith("-")) filtered.sortedByDescending { it.timestamp } else filtered.sortedBy { it.timestamp }
                    "totalBytes", "-totalBytes" -> if (field.startsWith("-")) filtered.sortedByDescending { it.totalSize } else filtered.sortedBy { it.totalSize }
                    else -> filtered
                }
            }
        } else {
            // default order by startTime descending
            filtered = filtered.sortedByDescending { it.timestamp }
        }

        if (query.has("limit")) {
            val limit = query.getInt("limit")
            filtered = filtered.take(limit)
        }

        val array = JSONArray()
        for (item in filtered) {
            array.put(formatDownloadItem(item))
        }
        return array
    }

    suspend fun pause(sender: ExtensionSender, downloadId: Long): JSONObject {
        verifyDownloadsPermission(sender)
        val allDownloads = downloadEngine.getDownloadsFlow().first()
        val item = allDownloads.find { it.id == downloadId }
            ?: throw IllegalArgumentException("DOWNLOAD_NOT_FOUND")

        if (item.status.uppercase() == "COMPLETED" || item.status.uppercase() == "FAILED" || item.status.uppercase() == "CANCELLED") {
            throw IllegalArgumentException("DOWNLOAD_PAUSE_UNSUPPORTED")
        }

        downloadEngine.pauseDownload(downloadId)

        eventManager.triggerEvent("downloads.onChanged", JSONObject().apply {
            put("id", downloadId)
            put("paused", JSONObject().apply { put("currentValue", true) })
        })

        return JSONObject().apply { put("status", "success") }
    }

    suspend fun resume(sender: ExtensionSender, downloadId: Long): JSONObject {
        verifyDownloadsPermission(sender)
        val allDownloads = downloadEngine.getDownloadsFlow().first()
        val item = allDownloads.find { it.id == downloadId }
            ?: throw IllegalArgumentException("DOWNLOAD_NOT_FOUND")

        if (item.status.equals("COMPLETED", ignoreCase = true) || item.status.equals("CANCELLED", ignoreCase = true) || item.status.equals("FAILED", ignoreCase = true)) {
            throw IllegalArgumentException("DOWNLOAD_RESUME_UNSUPPORTED")
        }

        downloadEngine.resumeDownload(downloadId)

        eventManager.triggerEvent("downloads.onChanged", JSONObject().apply {
            put("id", downloadId)
            put("paused", JSONObject().apply { put("currentValue", false) })
        })

        return JSONObject().apply { put("status", "success") }
    }

    suspend fun cancel(sender: ExtensionSender, downloadId: Long): JSONObject {
        verifyDownloadsPermission(sender)
        val allDownloads = downloadEngine.getDownloadsFlow().first()
        val item = allDownloads.find { it.id == downloadId }
            ?: throw IllegalArgumentException("DOWNLOAD_NOT_FOUND")

        downloadEngine.cancelDownload(downloadId)

        eventManager.triggerEvent("downloads.onChanged", JSONObject().apply {
            put("id", downloadId)
            put("state", JSONObject().apply { put("currentValue", "interrupted") })
        })

        return JSONObject().apply { put("status", "success") }
    }

    suspend fun removeFile(sender: ExtensionSender, downloadId: Long): JSONObject {
        verifyDownloadsPermission(sender)
        val allDownloads = downloadEngine.getDownloadsFlow().first()
        val item = allDownloads.find { it.id == downloadId }
            ?: throw IllegalArgumentException("DOWNLOAD_NOT_FOUND")

        downloadEngine.deleteDownload(downloadId)

        eventManager.triggerEvent("downloads.onErased", JSONObject().apply {
            put("id", downloadId)
        })

        return JSONObject().apply { put("status", "success") }
    }

    suspend fun erase(sender: ExtensionSender, query: JSONObject): JSONArray {
        verifyDownloadsPermission(sender)
        val matches = search(sender, query)
        val erasedIds = JSONArray()

        for (i in 0 until matches.length()) {
            val item = matches.getJSONObject(i)
            val id = item.getLong("id")
            downloadEngine.deleteDownload(id)
            erasedIds.put(id)

            eventManager.triggerEvent("downloads.onErased", JSONObject().apply {
                put("id", id)
            })
        }

        return erasedIds
    }

    suspend fun open(sender: ExtensionSender, downloadId: Long): JSONObject {
        verifyDownloadsPermission(sender)
        val allDownloads = downloadEngine.getDownloadsFlow().first()
        val item = allDownloads.find { it.id == downloadId }
            ?: throw IllegalArgumentException("DOWNLOAD_NOT_FOUND")

        val filePath = item.filePath
        if (filePath.isBlank()) {
            throw IllegalArgumentException("DOWNLOAD_FILE_UNAVAILABLE")
        }
        val file = java.io.File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("DOWNLOAD_FILE_UNAVAILABLE")
        }

        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, item.mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            throw IllegalArgumentException("DOWNLOAD_OPEN_UNSUPPORTED")
        }
        return JSONObject().apply { put("status", "success") }
    }
}
