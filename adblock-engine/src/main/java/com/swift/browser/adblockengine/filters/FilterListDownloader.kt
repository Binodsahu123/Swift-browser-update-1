package com.swift.browser.adblockengine.filters

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads lists asynchronously using HttpURLConnection with strict connect/read limits.
 */
object FilterListDownloader {
    private const val TAG = "FilterListDownloader"

    suspend fun download(urlString: String): List<String> = withContext(Dispatchers.IO) {
        val rules = ArrayList<String>()
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.useCaches = false
            
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("[")) {
                            rules.add(trimmed)
                        }
                    }
                }
                Log.i(TAG, "Completed downloading ${rules.size} entries from $urlString")
            } else {
                Log.w(TAG, "Server code failed for $urlString: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network download exception on $urlString", e)
        }
        rules
    }
}
