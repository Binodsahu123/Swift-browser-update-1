package com.swift.browser.downloadengine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object DownloadQueueManager {
    private const val TAG = "DownloadQueueManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    
    // Status callbacks
    private var onProgressCallback: ((DownloadItem) -> Unit)? = null
    private var logListener: ((String, String, String, String) -> Unit)? = null

    fun setProgressCallback(callback: (DownloadItem) -> Unit) {
        onProgressCallback = callback
    }

    fun setLogListener(listener: (tag: String, message: String, level: String, component: String) -> Unit) {
        logListener = listener
    }

    private fun isNetworkAvailable(context: Context, wifiOnly: Boolean): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return if (wifiOnly) {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }

    fun startDownloadTask(
        context: Context,
        item: DownloadItem,
        config: DownloadConfig,
        onUpdate: suspend (DownloadItem) -> Unit
    ) {
        // Cancel active one if exists
        cancelTask(item.id)

        logListener?.invoke(TAG, "Queue worker starting download task: ${item.title} (ID: ${item.id})", "INFO", "DOWNLOADER")

        val job = scope.launch {
            try {
                if (!isNetworkAvailable(context, config.wifiOnly)) {
                    logListener?.invoke(TAG, "Download failed: Network connection not available (wifiOnly = ${config.wifiOnly})", "ERROR", "DOWNLOADER")
                    val updated = item.copy(status = "FAILED", speed = "No internet / Wi-Fi needed")
                    onUpdate(updated)
                    onProgressCallback?.invoke(updated)
                    return@launch
                }

                // Transition to RUNNING
                var currentItem = item.copy(status = "RUNNING", speed = "Connecting...")
                onUpdate(currentItem)
                onProgressCallback?.invoke(currentItem)

                logListener?.invoke(TAG, "Connecting to media source URL: ${currentItem.url}", "INFO", "DOWNLOADER")

                val urlSpec = URL(currentItem.url)
                val testConn = urlSpec.openConnection() as HttpURLConnection
                testConn.requestMethod = "HEAD"
                testConn.connectTimeout = 5000
                testConn.readTimeout = 5000
                testConn.useCaches = false
                
                val responseCode = testConn.responseCode
                val totalLength = if (responseCode in 200..299) testConn.contentLengthLong else 0L
                val acceptRanges = testConn.getHeaderField("Accept-Ranges") == "bytes"
                testConn.disconnect()

                logListener?.invoke(TAG, "Connection response: Code $responseCode, Size: $totalLength bytes, Range resume supported: $acceptRanges", "INFO", "DOWNLOADER")

                val category = DownloadScheduler.getCategoryForMimeType(currentItem.mimeType)
                val finalFile = if (currentItem.filePath.isEmpty()) {
                    DownloadScheduler.getOutputFile(context, category, currentItem.title)
                } else {
                    File(currentItem.filePath)
                }

                currentItem = currentItem.copy(
                    totalSize = if (totalLength > 0) totalLength else currentItem.totalSize,
                    filePath = finalFile.absolutePath,
                    category = category
                )
                onUpdate(currentItem)

                if (totalLength > 1024 * 1024 && acceptRanges && currentItem.threads > 1) {
                    logListener?.invoke(TAG, "Starting multi-threaded segment assembly (${currentItem.threads} channels)", "INFO", "DOWNLOADER")
                    // Multi-threaded chunk downloading
                    downloadInChunks(context, currentItem, finalFile, config, onUpdate)
                } else {
                    logListener?.invoke(TAG, "Starting single-stream sequential chunk downloading", "INFO", "DOWNLOADER")
                    // Single thread standard stream downloader with progress tick
                    downloadSingleStream(context, currentItem, finalFile, onUpdate)
                }
            } catch (e: CancellationException) {
                logListener?.invoke(TAG, "Download job for ${item.title} paused/cancelled by user.", "WARNING", "DOWNLOADER")
                val updated = item.copy(status = "PAUSED", speed = "Paused")
                onUpdate(updated)
                onProgressCallback?.invoke(updated)
            } catch (e: Exception) {
                logListener?.invoke(TAG, "Download failed with runtime error: ${e.localizedMessage}", "ERROR", "DOWNLOADER")
                Log.e(TAG, "Download error standard: ${e.message}", e)
                val updated = item.copy(status = "FAILED", speed = "Error: ${e.localizedMessage}")
                onUpdate(updated)
                onProgressCallback?.invoke(updated)
            } finally {
                activeJobs.remove(item.id)
            }
        }
        
        activeJobs[item.id] = job
    }

    private suspend fun downloadSingleStream(
        context: Context,
        item: DownloadItem,
        destFile: File,
        onUpdate: suspend (DownloadItem) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = URL(item.url)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        
        // Resume if file partially exists
        val existingLen = if (destFile.exists()) destFile.length() else 0L
        if (existingLen > 0) {
            conn.setRequestProperty("Range", "bytes=$existingLen-")
        }

        conn.connect()
        val responseCode = conn.responseCode
        val isResume = responseCode == HttpURLConnection.HTTP_PARTIAL
        val totalBytes = if (isResume) item.totalSize else (conn.contentLengthLong + existingLen).coerceAtLeast(item.totalSize)

        val mode = if (isResume) "rw" else "rwd"
        val output = RandomAccessFile(destFile, mode)
        if (isResume) {
            output.seek(existingLen)
        } else {
            output.setLength(0)
        }

        val bis = BufferedInputStream(conn.inputStream)
        val buffer = ByteArray(16384)
        var bytesRead: Int
        var downloaded = existingLen
        var lastTime = System.currentTimeMillis()
        var lastDownloaded = existingLen

        while (true) {
            ensureActive()
            bytesRead = bis.read(buffer)
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead

            val now = System.currentTimeMillis()
            if (now - lastTime >= 1000) {
                val speedBytes = (downloaded - lastDownloaded) * 1000L / (now - lastTime)
                val speedText = formatSpeed(speedBytes)
                val progress = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                
                val current = item.copy(
                    status = "RUNNING",
                    progress = progress,
                    downloadedSize = downloaded,
                    totalSize = totalBytes,
                    speed = speedText
                )
                onUpdate(current)
                onProgressCallback?.invoke(current)
                
                lastTime = now
                lastDownloaded = downloaded
            }
        }

        bis.close()
        output.close()
        conn.disconnect()

        logListener?.invoke(TAG, "Download completed successfully (Single-Stream): ${item.title} -> ${destFile.name}", "SUCCESS", "DOWNLOADER")

        val completed = item.copy(
            status = "COMPLETED",
            progress = 100,
            downloadedSize = totalBytes,
            totalSize = totalBytes,
            speed = "Completed"
        )
        onUpdate(completed)
        onProgressCallback?.invoke(completed)
    }

    private suspend fun downloadInChunks(
        context: Context,
        item: DownloadItem,
        destFile: File,
        config: DownloadConfig,
        onUpdate: suspend (DownloadItem) -> Unit
    ) = withContext(Dispatchers.IO) {
        val numChunks = item.threads
        val totalSize = item.totalSize
        val chunkSize = totalSize / numChunks
        val partFiles = Array(numChunks) { idx -> File(destFile.parent, "${destFile.name}.part$idx") }
        
        val progressMap = ConcurrentHashMap<Int, Long>()
        var globalDownloaded = 0L
        var lastTime = System.currentTimeMillis()
        var lastDownloaded = 0L

        val deferreds = (0 until numChunks).map { index ->
            async(Dispatchers.IO) {
                val start = index * chunkSize
                val end = if (index == numChunks - 1) totalSize - 1 else (start + chunkSize - 1)
                val partFile = partFiles[index]

                var trials = 3
                var success = false
                while (trials > 0 && !success) {
                    try {
                        ensureActive()
                        val url = URL(item.url)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        
                        // Check if part file already fully downloaded or partially
                        val resumeByte = if (partFile.exists()) partFile.length() else 0L
                        val newStart = start + resumeByte
                        
                        if (newStart >= end) {
                            progressMap[index] = end - start + 1
                            success = true
                            break
                        }

                        conn.setRequestProperty("Range", "bytes=$newStart-$end")
                        conn.connect()

                        val input = conn.inputStream
                        val output = RandomAccessFile(partFile, "rw")
                        output.seek(resumeByte)

                        val buffer = ByteArray(8192)
                        var read: Int
                        while (true) {
                            ensureActive()
                            read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            
                            val currentPartLen = output.length()
                            progressMap[index] = currentPartLen
                            
                            // Periodically update progress
                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 1000) {
                                globalDownloaded = progressMap.values.sum()
                                val speedBytes = (globalDownloaded - lastDownloaded) * 1000L / (now - lastTime)
                                val speedText = formatSpeed(speedBytes)
                                val progress = if (totalSize > 0) ((globalDownloaded * 100) / totalSize).toInt() else 0
                                
                                val current = item.copy(
                                    status = "RUNNING",
                                    progress = progress,
                                    downloadedSize = globalDownloaded,
                                    speed = speedText
                                )
                                onUpdate(current)
                                onProgressCallback?.invoke(current)
                                
                                lastTime = now
                                lastDownloaded = globalDownloaded
                            }
                        }

                        output.close()
                        input.close()
                        conn.disconnect()
                        success = true
                    } catch (e: Exception) {
                        trials--
                        if (trials == 0) throw e
                        delay(1000)
                    }
                }
            }
        }

        deferreds.awaitAll()

        // Merge split parts natively or optimized fallback
        val partPaths = partFiles.map { it.absolutePath }
        logListener?.invoke(TAG, "All segments downloaded. Running native ChunkAssembler to merge ${partPaths.size} parts...", "INFO", "DOWNLOADER")
        com.swift.browser.nativedownloadengine.NativeDownloadEngine.assembleChunks(partPaths, destFile.absolutePath)
        
        // Ensure all temp part files are cleaned up
        partFiles.forEach { part ->
            try {
                if (part.exists()) part.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean up temp part file: ${part.absolutePath}", e)
            }
        }

        logListener?.invoke(TAG, "Native ChunkAssembler completed. Created final file: ${destFile.name}", "SUCCESS", "DOWNLOADER")

        val completed = item.copy(
            status = "COMPLETED",
            progress = 100,
            downloadedSize = totalSize,
            speed = "Completed"
        )
        onUpdate(completed)
        onProgressCallback?.invoke(completed)
    }

    fun cancelTask(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec.toDouble() / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%d KB/s", bytesPerSec / 1024)
            else -> "$bytesPerSec B/s"
        }
    }
}
