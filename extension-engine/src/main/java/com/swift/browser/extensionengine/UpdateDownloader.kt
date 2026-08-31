package com.swift.browser.extensionengine

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateDownloader(
    private val context: Context,
    private val maxSizeBytes: Long = 100 * 1024 * 1024L // 100 MB max limit
) {

    /**
     * Downloads an extension package from a remote update URL cleanly.
     * Enforces maximum size bounds to prevent memory exhaustion and zip bomb payloads.
     */
    fun downloadPackage(codebaseUrl: String): ByteArray {
        val url = try {
            URL(codebaseUrl)
        } catch (e: Exception) {
            throw ExtensionError.InstallerError.FileSystemError(codebaseUrl, e)
        }

        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw ExtensionError.InstallerError.FileSystemError(
                codebaseUrl,
                Exception("HTTP download failed with status code $responseCode")
            )
        }

        val contentLength = connection.contentLengthLong
        if (contentLength > maxSizeBytes) {
            throw ExtensionError.InstallerError.QuotaExceeded(contentLength, maxSizeBytes)
        }

        val inputStream: InputStream = connection.inputStream
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0L

        inputStream.use { input ->
            var read = input.read(buffer)
            while (read != -1) {
                totalRead += read
                if (totalRead > maxSizeBytes) {
                    throw ExtensionError.InstallerError.QuotaExceeded(totalRead, maxSizeBytes)
                }
                outputStream.write(buffer, 0, read)
                read = input.read(buffer)
            }
        }

        val downloadedBytes = outputStream.toByteArray()
        if (downloadedBytes.isEmpty()) {
            throw ExtensionError.InstallerError.InvalidArchiveFormat("Downloaded package from $codebaseUrl is empty (0 bytes)")
        }

        return downloadedBytes
    }
}
