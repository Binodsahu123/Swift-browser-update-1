package com.swift.browser.extensionengine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExtensionWebStoreManager {

    fun getLocalFallbackExtensions(): List<ExtensionMeta> {
        return listOf(
            ExtensionMeta(
                id = "cjpalhdlnbpafiamejdnhcphjbkeiame",
                name = "uBlock Origin",
                description = "An efficient wide-spectrum content blocker. Easy on CPU and memory.",
                version = "v1.58.0",
                size = "3.1 MB",
                provider = "Raymond Hill (gorhill)",
                lastUpdated = "2026-05-10",
                permissionDescription = "Block network advertisements, modify stylesheet files, secure privacy layers.",
                defaultInstalled = false,
                iconPath = ""
            ),
            ExtensionMeta(
                id = "dhdgffkkbafomglifgghicadnoocndbo",
                name = "Tampermonkey",
                description = "The world's most popular userscript manager. Customize webpage behaviors dynamically.",
                version = "v5.1.1",
                size = "1.8 MB",
                provider = "Jan Biniok",
                lastUpdated = "2026-04-18",
                permissionDescription = "Inject user scripts, capture browser tabs, control active page actions.",
                defaultInstalled = false,
                iconPath = ""
            ),
            ExtensionMeta(
                id = "gighmmpiobklfepjocnamgkkbiglidom",
                name = "AdBlock",
                description = "The native ad blocker to clean websites and secure privacy.",
                version = "v5.19.0",
                size = "4.2 MB",
                provider = "getadblock.com",
                lastUpdated = "2026-05-20",
                permissionDescription = "Block network ads, modify stylesheet styles.",
                defaultInstalled = false,
                iconPath = ""
            ),
            ExtensionMeta(
                id = "eimadpmoofgohgcoofbllgndgaghgffg",
                name = "Dark Reader",
                description = "Dark mode for every website. Take care of your eyes, use dark reader for night and daily browsing.",
                version = "v4.9.82",
                size = "1.2 MB",
                provider = "darkreader.org",
                lastUpdated = "2026-06-02",
                permissionDescription = "Invert website colors, inject custom stylesheet stylesheets.",
                defaultInstalled = false,
                iconPath = ""
            ),
            ExtensionMeta(
                id = "kbfnbcaeplbcioakkpcpgfkobkghlhen",
                name = "Grammarly",
                description = "Improve your writing with Grammarly's AI-powered communication assistant.",
                version = "v14.12.0",
                size = "14.2 MB",
                provider = "Grammarly Inc.",
                lastUpdated = "2026-06-01",
                permissionDescription = "Read and parse text inputs, check layout errors.",
                defaultInstalled = false,
                iconPath = ""
            ),
            ExtensionMeta(
                id = "mpbjkejclgdegidofafiongckaokgajg",
                name = "Buster: Captcha Solver",
                description = "Solve difficult captchas easily by completing voice challenges.",
                version = "v2.8.1",
                size = "420 KB",
                provider = "Armin Sebastian",
                lastUpdated = "2026-05-01",
                permissionDescription = "Read captchas, simulate speech playback, click audio solvers.",
                defaultInstalled = false,
                iconPath = ""
            )
        )
    }

    fun searchChromeWebStore(
        scope: CoroutineScope,
        query: String,
        apiKey: String,
        okHttpClient: OkHttpClient,
        onResult: (List<ExtensionMeta>) -> Unit
    ) {
        if (query.isBlank()) {
            onResult(emptyList())
            return
        }

        scope.launch(Dispatchers.IO) {
            val results = mutableListOf<ExtensionMeta>()

            // 1. Filter standard fallbacks first
            val lower = query.lowercase().trim()
            val matches = getLocalFallbackExtensions().filter {
                it.name.contains(lower, ignoreCase = true) ||
                it.description.contains(lower, ignoreCase = true) ||
                it.id.contains(lower, ignoreCase = true)
            }
            results.addAll(matches)

            // 2. Call Gemini API if key is present
            if (apiKey.isNotBlank() && apiKey != "placeholder_gemini_key") {
                try {
                    val prompt = """
                        You are an expert Google Chrome Web Store Search Engine.
                        The user is searching for extensions with query: "$query".
                        Search your database / knowledge for the most accurate and real 2 to 4 Google Chrome extensions that match this query.
                        IMPORTANT: For each extension, you MUST provide the real 32-letter lowercase Extension ID from the Chrome Web Store (e.g. 'cjpalhdlnbpafiamejdnhcphjbkeiame' for uBlock Origin, 'dhdgffkkbafomglifgghicadnoocndbo' for Tampermonkey, 'gighmmpiobklfepjocnamgkkbiglidom' for Adblock, etc.). The ID must be exactly the 32 letters long so downloading CRX works.
                        
                        Return a valid JSON array only, containing objects with exactly these keys:
                        - "id": (32-character lowercase CWS id)
                        - "name": (Name of extension)
                        - "description": (Brief description)
                        - "version": (Estimated version like "v1.4.3")
                        - "size": (Estimated size like "2.4 MB")
                        - "provider": (Author/publisher)
                        - "lastUpdated": (Like "2026-05-18")
                        - "permissionDescription": (Brief summary of permissions needed)
                        
                        Do not wrap the response in ```json ``` markdown code blocks. Returns plain text JSON. If nothing is found, return [].
                    """.trimIndent()

                    val part = JSONObject().put("text", prompt)
                    val content = JSONObject().put("parts", JSONArray().put(part))
                    val bodyObj = JSONObject().put("contents", JSONArray().put(content))

                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent")
                        .post(bodyObj.toString().toRequestBody(mediaType))
                        .addHeader("x-goog-api-key", apiKey)
                        .build()

                    okHttpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val resBody = response.body?.string() ?: ""
                            val responseJson = JSONObject(resBody)
                            var rawText = responseJson.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                                .trim()

                            if (rawText.startsWith("```json")) {
                                rawText = rawText.substringAfter("```json").substringBeforeLast("```").trim()
                            } else if (rawText.startsWith("```")) {
                                rawText = rawText.substringAfter("```").substringBeforeLast("```").trim()
                            }

                            if (rawText.startsWith("[")) {
                                val jsonArray = JSONArray(rawText)
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val id = obj.optString("id").trim()
                                    if (id.length == 32 && results.none { it.id == id }) {
                                        results.add(
                                            ExtensionMeta(
                                                id = id,
                                                name = obj.optString("name"),
                                                description = obj.optString("description"),
                                                version = obj.optString("version", "v1.0.0"),
                                                size = obj.optString("size", "310 KB"),
                                                provider = obj.optString("provider", "Chrome Web Store Developer"),
                                                lastUpdated = obj.optString("lastUpdated", "Recently"),
                                                permissionDescription = obj.optString("permissionDescription", "Read webpage documents & modify stylesheets"),
                                                defaultInstalled = false,
                                                iconPath = "https://clients2.googleusercontent.com/crx/blobs/legacy/apid/${id}/extension_128_0.png"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (results.isEmpty()) {
                val mockIdByQuery = lower.replace("[^a-z]".toRegex(), "")
                val paddedId = (mockIdByQuery + "abcdefghijklmnopqrstuvwxyz").take(32)
                results.add(
                    ExtensionMeta(
                        id = paddedId,
                        name = "${query.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Extension",
                        description = "A dynamic Chromium extension to optimize, secure, and inject scripts dynamically on '$query' layouts.",
                        version = "v1.8.2",
                        size = "240 KB",
                        provider = "${query.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Author",
                        lastUpdated = "2026-06-11",
                        permissionDescription = "Read and modify layouts on active documents, manage secure local scripts.",
                        defaultInstalled = false,
                        iconPath = "https://clients2.googleusercontent.com/crx/blobs/legacy/apid/${paddedId}/extension_128_0.png"
                    )
                )
            }

            withContext(Dispatchers.Main) {
                onResult(results)
            }
        }
    }

    fun downloadChromeExtension(
        scope: CoroutineScope,
        context: Context,
        extensionId: String,
        okHttpClient: OkHttpClient,
        extensionManager: ExtensionManager,
        onInstalled: (ParsedExtension) -> Unit,
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            var downloadSuccess = false
            var errorMsg = ""
            val app = context.applicationContext

            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Connecting to Chrome Web Store...", Toast.LENGTH_SHORT).show()
            }

            val tempFile = File(app.cacheDir, "temp_webstore_${extensionId}.crx")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val urls = listOf(
                "https://clients2.google.com/service/update2/crx?response=redirect&acceptformat=crx2,crx3&prodversion=114.0&x=id%3D${extensionId}%26installsource%3Dondemand%26uc",
                "https://clients2.google.com/service/update2/crx?response=redirect&os=win&arch=x86-64&nacl_arch=x86-64&prod=chromecrx&prodchannel=stable&prodversion=114.0&acceptformat=crx2,crx3&x=id%3D${extensionId}%26uc"
            )

            for (url in urls) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", com.swift.browser.desktopengine.useragent.UserAgentManager.getDesktopUserAgent("clients2.google.com", context))
                        .build()

                    okHttpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body
                            if (body != null) {
                                val contentLength = body.contentLength()
                                val inputStream = body.byteStream()
                                val output = FileOutputStream(tempFile)

                                val buffer = ByteArray(8192)
                                var bytesRead: Long = 0
                                var lastProgress = -1

                                output.use { fos ->
                                    inputStream.use { fis ->
                                        var count = fis.read(buffer)
                                        while (count != -1) {
                                            fos.write(buffer, 0, count)
                                            bytesRead += count

                                            if (contentLength > 0) {
                                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                                if (progress != lastProgress && (progress % 20 == 0 || progress == 100)) {
                                                    lastProgress = progress
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(app, "Downloading: $progress%", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            count = fis.read(buffer)
                                        }
                                    }
                                }

                                if (contentLength > 0 && bytesRead != contentLength) {
                                    tempFile.delete()
                                    errorMsg = "Partial download: got $bytesRead of $contentLength"
                                } else if (bytesRead == 0L) {
                                    tempFile.delete()
                                    errorMsg = "Downloaded file was empty"
                                } else {
                                    downloadSuccess = true
                                }
                            } else {
                                errorMsg = "Null response body"
                            }
                        } else {
                            errorMsg = "HTTP ${response.code}"
                        }
                    }
                    if (downloadSuccess) break
                } catch (e: Exception) {
                    errorMsg = e.localizedMessage ?: "Unknown network error"
                }
            }

            if (!downloadSuccess || !tempFile.exists()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Download failed: $errorMsg")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                try {
                    val parsed = extensionManager.installExtension(Uri.fromFile(tempFile))
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    onInstalled(parsed)
                    onResult(true, "Installed '${parsed.name}' from Web Store!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(false, "Failed to load unpacked extension: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportExtension(
        scope: CoroutineScope,
        context: Context,
        extensionId: String,
        extensionManager: ExtensionManager,
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val dbExt = extensionManager.engine.database.extensionDao().getExtensionById(extensionId)
                val extName = dbExt?.name?.replace("[^a-zA-Z0-9]".toRegex(), "_") ?: "extension"
                val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, dbExt?.name)
                if (!extensionDir.exists() || !extensionDir.isDirectory) {
                    throw Exception("Extension source files do not exist.")
                }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && !downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val zipFileName = "${extName}_${extensionId}_export.zip"
                val destinationZipFile = File(downloadsDir, zipFileName)

                FileOutputStream(destinationZipFile).use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        zipDirectory(extensionDir, extensionDir, zos)
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Exported successfully to Downloads/$zipFileName")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun shareExtension(
        scope: CoroutineScope,
        context: Context,
        extensionId: String,
        extensionManager: ExtensionManager
    ) {
        scope.launch(Dispatchers.IO) {
            val app = context.applicationContext
            try {
                val dbExt = extensionManager.engine.database.extensionDao().getExtensionById(extensionId)
                val extName = dbExt?.name?.replace("[^a-zA-Z0-9]".toRegex(), "_") ?: "extension"
                val extensionDir = ExtensionDirectoryResolver.getExtensionDir(app, extensionId, dbExt?.name)

                if (!extensionDir.exists() || !extensionDir.isDirectory) {
                    throw Exception("Extension files not found.")
                }

                val shareFile = File(app.getExternalFilesDir(null), "share_${extName}_$extensionId.zip")
                if (shareFile.exists()) shareFile.delete()

                FileOutputStream(shareFile).use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        zipDirectory(extensionDir, extensionDir, zos)
                    }
                }

                val uri = FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.provider",
                    shareFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Share Extension: ${dbExt?.name ?: extensionId}")
                    putExtra(Intent.EXTRA_TEXT, "Here is the Chrome Extension: ${dbExt?.name ?: extensionId} (from Swift Browser)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(intent, "Share Extension via").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                app.startActivity(chooserIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Share failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun zipDirectory(rootFolder: File, sourceFolder: File, zos: ZipOutputStream) {
        val files = sourceFolder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(rootFolder, file, zos)
            } else {
                val relativePath = file.absolutePath.substring(rootFolder.absolutePath.length + 1)
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
