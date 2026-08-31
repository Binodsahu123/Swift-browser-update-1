package com.swift.browser.vpnengine.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class DownloadConfig(
    val url: String,
    val providerName: String
)

class VpnDownloadManager {
    private val _downloadProgress = MutableStateFlow<Float>(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

suspend fun downloadProviderConfig(config: DownloadConfig): File? {
        _isDownloading.value = true
        _downloadProgress.value = 0f
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var file: File? = null
            try {
                val url = java.net.URL(config.url)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream
                    file = File.createTempFile("provider_config", ".ovpn")
                    val outputStream = java.io.FileOutputStream(file)

                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    
                    while (inputStream.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            _downloadProgress.value = (total.toFloat() / fileLength)
                        } else {
                            // If length unknown, just toggle progress
                            _downloadProgress.value = (_downloadProgress.value + 0.1f) % 1.0f
                        }
                        outputStream.write(data, 0, count)
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isDownloading.value = false
            }
            file
        }
    }


}
