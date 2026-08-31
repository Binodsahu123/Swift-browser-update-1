package com.swift.browser.nativedownloadengine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object NativeDownloadEngine {
    private const val TAG = "NativeDownloadEngine"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("native_media_bridge")
            isNativeLoaded = true
            Log.i(TAG, "Native download optimization module loaded successfully via native_media_bridge.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library 'native_media_bridge' not loaded. Falling back to robust JVM implementation.")
            isNativeLoaded = false
        }
    }

    /**
     * Splits a single large download task into equal byte range offsets natively.
     */
    fun calculateDownloadChunks(fileSize: Long, numThreads: Int): String {
        if (isNativeLoaded) {
            try {
                return nativeCalculateChunks(fileSize, numThreads)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native calculate download chunks failed; executing fallback", e)
            }
        }
        return fallbackCalculateChunks(fileSize, numThreads)
    }

    /**
     * Verifies file integrity by checking files directly via optimized direct byte-access.
     */
    fun verifyFileIntegrity(filePath: String, expectedSHA256: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeVerifyFileIntegrity(filePath, expectedSHA256)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native file verification failed; executing fallback", e)
            }
        }
        return fallbackVerifyFileIntegrity(filePath, expectedSHA256)
    }

    /**
     * Natively merges chunks to minimize file descriptor thrashing and garbage collection spikes.
     */
    fun assembleChunks(chunkPaths: List<String>, outputPath: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeAssembleChunks(chunkPaths.toTypedArray(), outputPath)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native chunk assembly failed; executing fallback", e)
            }
        }
        return fallbackAssembleChunks(chunkPaths, outputPath)
    }

    private external fun nativeCalculateChunks(fileSize: Long, numThreads: Int): String
    private external fun nativeVerifyFileIntegrity(filePath: String, expectedHash: String): Boolean
    private external fun nativeAssembleChunks(chunkPaths: Array<String>, outputPath: String): Boolean

    private fun fallbackCalculateChunks(fileSize: Long, numThreads: Int): String {
        val result = JSONObject()
        val chunksArray = JSONArray()
        try {
            val threads = if (numThreads <= 0) 1 else numThreads
            val chunkSize = fileSize / threads
            var startByte: Long = 0

            for (i in 0 until threads) {
                val endByte = if (i == threads - 1) fileSize - 1 else startByte + chunkSize - 1
                val chunk = JSONObject().apply {
                    put("chunkIndex", i)
                    put("startByte", startByte)
                    put("endByte", endByte)
                    put("status", "READY")
                }
                chunksArray.put(chunk)
                startByte = endByte + 1
            }

            result.put("status", "success")
            result.put("totalSize", fileSize)
            result.put("chunksCount", threads)
            result.put("chunks", chunksArray)
            result.put("mode", "fallback_jvm")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating chunks fallback", e)
            result.put("status", "error")
            result.put("message", e.message)
        }
        return result.toString()
    }

    private fun fallbackVerifyFileIntegrity(filePath: String, expectedSHA256: String): Boolean {
        if (expectedSHA256.isBlank()) return true
        val file = File(filePath)
        if (!file.exists()) return false

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            val fis = FileInputStream(file)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
            fis.close()

            val hashedBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashedBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString().trim().equals(expectedSHA256.trim(), ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Checksum verification fallback failed", e)
            false
        }
    }

    private fun fallbackAssembleChunks(chunkPaths: List<String>, outputPath: String): Boolean {
        val outputFile = File(outputPath)
        try {
            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(65536) // 64KB chunks merge loop
                for (path in chunkPaths) {
                    val chunkFile = File(path)
                    if (chunkFile.exists()) {
                        FileInputStream(chunkFile).use { fis ->
                            var read = fis.read(buffer)
                            while (read != -1) {
                                fos.write(buffer, 0, read)
                                read = fis.read(buffer)
                            }
                        }
                    } else {
                        Log.e(TAG, "Chunk file missing: $path")
                        return false
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Chunk merge process fallback failed", e)
            return false
        }
    }
}
