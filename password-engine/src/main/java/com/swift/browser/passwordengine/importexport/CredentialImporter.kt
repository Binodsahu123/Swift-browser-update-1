package com.swift.browser.passwordengine.importexport

import android.content.Context
import android.net.Uri
import com.swift.browser.passwordengine.repository.PasswordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class RawImportCredential(
    val siteUrl: String,
    val siteTitle: String = "",
    val username: String,
    val password: String,
    val notes: String = "",
    val category: String = "General"
)

data class ImportSummary(
    val totalParsed: Int = 0,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val errors: List<String> = emptyList()
)

class CredentialImporter(private val repository: PasswordRepository) {

    suspend fun importFromUri(context: Context, uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val inputStream = try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            return@withContext ImportSummary(errors = listOf("Unable to open file stream: ${e.message}"))
        } ?: return@withContext ImportSummary(errors = listOf("Unable to open file stream from selected URI"))

        val content = inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }

        return@withContext importFromText(content)
    }

    suspend fun importFromText(rawText: String): ImportSummary = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return@withContext ImportSummary(errors = listOf("File or input text is empty"))
        }

        val credentials = if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJsonData(trimmed)
        } else {
            parseCsvData(trimmed)
        }

        if (credentials.isEmpty()) {
            return@withContext ImportSummary(
                errors = listOf("No valid login credentials found in the file or text")
            )
        }

        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        for ((index, raw) in credentials.withIndex()) {
            if (raw.username.isBlank() && raw.password.isBlank()) {
                skipped++
                continue
            }
            if (raw.password.isBlank()) {
                skipped++
                errors.add("Row ${index + 1}: Skipped credential for '${raw.siteUrl}' due to empty password.")
                continue
            }

            val finalUrl = sanitizeUrl(raw.siteUrl)
            val finalTitle = if (raw.siteTitle.isNotBlank()) raw.siteTitle else extractTitleFromUrl(finalUrl)

            try {
                repository.savePassword(
                    siteUrl = finalUrl,
                    siteTitle = finalTitle,
                    username = raw.username,
                    rawPassword = raw.password,
                    notes = raw.notes,
                    category = if (raw.category.isNotBlank()) raw.category else "General"
                )
                imported++
            } catch (e: Exception) {
                skipped++
                errors.add("Row ${index + 1}: Error saving '${raw.username}' - ${e.message}")
            }
        }

        ImportSummary(
            totalParsed = credentials.size,
            importedCount = imported,
            skippedCount = skipped,
            errors = errors
        )
    }

    private fun parseCsvData(csvText: String): List<RawImportCredential> {
        val rows = parseCsvRows(csvText)
        if (rows.isEmpty()) return emptyList()

        val firstRow = rows.first()
        val headerMap = detectHeaderMap(firstRow)

        val startIndex = if (headerMap.hasHeader) 1 else 0
        val credentials = mutableListOf<RawImportCredential>()

        for (i in startIndex until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            val credential = if (headerMap.hasHeader) {
                mapRowWithHeader(row, headerMap)
            } else {
                mapRowFallback(row)
            }

            if (credential != null) {
                credentials.add(credential)
            }
        }

        return credentials
    }

    private data class HeaderMap(
        val hasHeader: Boolean,
        val urlIndex: Int = -1,
        val titleIndex: Int = -1,
        val usernameIndex: Int = -1,
        val passwordIndex: Int = -1,
        val notesIndex: Int = -1
    )

    private fun detectHeaderMap(row: List<String>): HeaderMap {
        var urlIdx = -1
        var titleIdx = -1
        var userIdx = -1
        var passIdx = -1
        var notesIdx = -1

        val lowerRow = row.map { it.trim().lowercase() }

        lowerRow.forEachIndexed { idx, col ->
            when {
                col in listOf("url", "website", "site", "login_uri", "uri", "domain", "login_url", "httprealm") -> urlIdx = idx
                col in listOf("title", "name", "site_title", "folder", "app") -> titleIdx = idx
                col in listOf("username", "login_username", "email", "login", "user", "user_name", "account") -> userIdx = idx
                col in listOf("password", "login_password", "pass", "pwd", "secret") -> passIdx = idx
                col in listOf("notes", "note", "memo", "fields", "extra") -> notesIdx = idx
            }
        }

        val hasHeader = (userIdx != -1 || passIdx != -1 || urlIdx != -1) &&
                lowerRow.any { col -> col in listOf("url", "website", "site", "username", "password", "title", "name", "email", "login", "pass") }

        return HeaderMap(
            hasHeader = hasHeader,
            urlIndex = urlIdx,
            titleIndex = titleIdx,
            usernameIndex = userIdx,
            passwordIndex = passIdx,
            notesIndex = notesIdx
        )
    }

    private fun mapRowWithHeader(row: List<String>, map: HeaderMap): RawImportCredential? {
        fun getCol(index: Int): String = if (index in row.indices) row[index].trim() else ""

        val url = getCol(map.urlIndex)
        val title = getCol(map.titleIndex)
        val username = getCol(map.usernameIndex)
        val password = getCol(map.passwordIndex)
        val notes = getCol(map.notesIndex)

        if (url.isBlank() && username.isBlank() && password.isBlank()) return null

        return RawImportCredential(
            siteUrl = url,
            siteTitle = title,
            username = username,
            password = password,
            notes = notes
        )
    }

    private fun mapRowFallback(row: List<String>): RawImportCredential? {
        if (row.size < 2) return null
        val trimmed = row.map { it.trim() }

        return when (row.size) {
            2 -> RawImportCredential(siteUrl = "", username = trimmed[0], password = trimmed[1])
            3 -> RawImportCredential(siteUrl = trimmed[0], username = trimmed[1], password = trimmed[2])
            else -> RawImportCredential(
                siteTitle = trimmed[0],
                siteUrl = trimmed[1],
                username = trimmed[2],
                password = trimmed[3],
                notes = if (row.size > 4) trimmed[4] else ""
            )
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var curField = StringBuilder()
        var curRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        curField.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    curRow.add(curField.toString().trim())
                    curField = StringBuilder()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                        i++
                    }
                    curRow.add(curField.toString().trim())
                    if (curRow.any { it.isNotBlank() }) {
                        result.add(curRow)
                    }
                    curRow = mutableListOf()
                    curField = StringBuilder()
                }
                else -> {
                    curField.append(c)
                }
            }
            i++
        }

        if (curField.isNotEmpty() || curRow.isNotEmpty()) {
            curRow.add(curField.toString().trim())
            if (curRow.any { it.isNotBlank() }) {
                result.add(curRow)
            }
        }

        return result
    }

    private fun parseJsonData(jsonText: String): List<RawImportCredential> {
        val list = mutableListOf<RawImportCredential>()
        try {
            if (jsonText.startsWith("[")) {
                val array = JSONArray(jsonText)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val cred = parseJsonObject(obj)
                    if (cred != null) list.add(cred)
                }
            } else if (jsonText.startsWith("{")) {
                val root = JSONObject(jsonText)
                if (root.has("items")) {
                    val items = root.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val obj = items.optJSONObject(i) ?: continue
                        val cred = parseJsonObject(obj)
                        if (cred != null) list.add(cred)
                    }
                } else if (root.has("logins")) {
                    val logins = root.optJSONArray("logins") ?: JSONArray()
                    for (i in 0 until logins.length()) {
                        val obj = logins.optJSONObject(i) ?: continue
                        val cred = parseJsonObject(obj)
                        if (cred != null) list.add(cred)
                    }
                }
            }
        } catch (e: Exception) {
            // Error parsing JSON
        }
        return list
    }

    private fun parseJsonObject(obj: JSONObject): RawImportCredential? {
        val url = obj.optString("url", obj.optString("siteUrl", obj.optString("login_uri", "")))
        val username = obj.optString("username", obj.optString("login_username", obj.optString("email", "")))
        val password = obj.optString("password", obj.optString("login_password", obj.optString("pass", "")))
        val title = obj.optString("title", obj.optString("name", obj.optString("siteTitle", "")))
        val notes = obj.optString("notes", obj.optString("notes", ""))

        if (username.isBlank() && password.isBlank()) return null

        return RawImportCredential(
            siteUrl = url,
            siteTitle = title,
            username = username,
            password = password,
            notes = notes
        )
    }

    private fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isNotBlank() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }

    private fun extractTitleFromUrl(url: String): String {
        var clean = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        return clean.ifBlank { "Imported Site" }
    }
}
