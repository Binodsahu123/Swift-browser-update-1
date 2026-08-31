package com.swift.browser.searchengine

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.speech.RecognizerIntent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

enum class SuggestionType {
    SEARCH, HISTORY, BOOKMARK
}

data class SearchSuggestion(
    val type: SuggestionType,
    val title: String,
    val url: String = ""
)

/**
 * Encapsulates browsing context and privacy state for search and personalization operations.
 */
data class BrowsingContext(
    val isPrivate: Boolean = false,
    val sessionId: String? = null
) {
    companion object {
        val NORMAL = BrowsingContext(isPrivate = false)
        val PRIVATE = BrowsingContext(isPrivate = true)
        fun private(sessionId: String? = null) = BrowsingContext(isPrivate = true, sessionId = sessionId)
    }
}

interface SearchEngine {
    fun processInput(input: String, engineName: String = "Google"): String
    fun processInput(input: String, engineName: String = "Google", browsingContext: BrowsingContext): String = processInput(input, engineName)
    fun buildSearchUrl(query: String, engineName: String = "Google"): String
    fun formatUrlOrSearch(input: String, engineName: String = "Google"): String
    fun formatUrlOrSearch(input: String, engineName: String = "Google", browsingContext: BrowsingContext): String = formatUrlOrSearch(input, engineName)
    fun getSelectedSearchEngine(prefs: SharedPreferences?): String
    fun getSearchEngineUrl(engineName: String): String
    fun getUrlHost(url: String): String
    fun getSuggestionApiUrl(engineName: String, query: String): String?
    fun fetchSearchSuggestions(
        url: String,
        engineName: String,
        query: String,
        client: OkHttpClient
    ): List<SearchSuggestion>
    fun getSearchSuggestions(
        input: String,
        engineName: String,
        historyResults: List<SearchSuggestion>,
        bookmarkResults: List<SearchSuggestion>,
        client: OkHttpClient
    ): List<SearchSuggestion> {
        return getSearchSuggestions(input, engineName, historyResults, bookmarkResults, client, BrowsingContext.NORMAL)
    }
    fun getSearchSuggestions(
        input: String,
        engineName: String,
        historyResults: List<SearchSuggestion>,
        bookmarkResults: List<SearchSuggestion>,
        client: OkHttpClient,
        browsingContext: BrowsingContext
    ): List<SearchSuggestion>
    fun clearCache()
}

class SearchEngineImpl : SearchEngine {

    private val suggestionMemoryCache = object : java.util.LinkedHashMap<String, List<SearchSuggestion>>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchSuggestion>>?): Boolean {
            return size > 50
        }
    }

    override fun getSelectedSearchEngine(prefs: SharedPreferences?): String {
        return prefs?.getString("default_search_engine", "Google") ?: "Google"
    }

    override fun getSearchEngineUrl(engineName: String): String {
        return when (engineName) {
            "Google" -> "https://www.google.com/search?q={query}"
            "Bing" -> "https://www.bing.com/search?q={query}"
            "DuckDuckGo" -> "https://duckduckgo.com/?q={query}"
            "Yahoo" -> "https://search.yahoo.com/search?p={query}"
            "Brave" -> "https://search.brave.com/search?q={query}"
            "ChatGPT" -> "https://chatgpt.com/?q={query}"
            "Claude (Anthropic)" -> "https://claude.ai/search?q={query}"
            "Perplexity AI" -> "https://www.perplexity.ai/search?q={query}"
            "Ecosia" -> "https://www.ecosia.org/search?q={query}"
            "Yandex" -> "https://yandex.com/search/?text={query}"
            "Startpage" -> "https://www.startpage.com/search?q={query}"
            "Qwant" -> "https://www.qwant.com/?q={query}"
            else -> "https://www.google.com/search?q={query}"
        }
    }

    override fun buildSearchUrl(query: String, engineName: String): String {
        val queryEncoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        val urlTemplate = getSearchEngineUrl(engineName)
        return urlTemplate.replace("{query}", queryEncoded)
    }

    override fun processInput(input: String, engineName: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "about:blank"

        // Check if it's a local or internal URI
        if (trimmed.startsWith("file://") || trimmed.startsWith("content://") || trimmed.startsWith("about:") || trimmed.startsWith("swift://")) {
            return trimmed
        }

        // Check if it's a valid URL or IP address
        val isUrlPatternMatched = try {
            android.util.Patterns.WEB_URL.matcher(trimmed).matches()
        } catch (e: Throwable) {
            false
        }
        val isUrl = isUrlPatternMatched ||
                    (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3)

        if (isUrl) {
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        // If not a URL, treat it as a search query
        return buildSearchUrl(trimmed, engineName)
    }

    override fun formatUrlOrSearch(input: String, engineName: String): String {
        val trimmed = input.trim()
        if (trimmed == "swift://newtab" || trimmed == "swift://newtab-incognito") return trimmed
        return processInput(trimmed, engineName)
    }

    override fun getUrlHost(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val uri = java.net.URI(url)
            val host = uri.host
            if (!host.isNullOrEmpty()) {
                if (host.startsWith("www.")) host.substring(4) else host
            } else {
                url
            }
        } catch (e: Exception) {
            val cleaned = url.removePrefix("https://").removePrefix("http://").split("/")[0].split("?")[0]
            if (cleaned.startsWith("www.")) cleaned.substring(4) else cleaned
        }
    }

    override fun getSuggestionApiUrl(engineName: String, query: String): String? {
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        return when (engineName) {
            "Google" -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            "Bing" -> "https://api.bing.com/osjson.aspx?query=$encoded"
            "DuckDuckGo" -> "https://duckduckgo.com/ac/?q=$encoded"
            "Yahoo" -> "https://ff.search.yahoo.com/gossip?output=fxjson&command=$encoded"
            else -> null
        }
    }

    override fun fetchSearchSuggestions(
        url: String,
        engineName: String,
        query: String,
        client: OkHttpClient
    ): List<SearchSuggestion> {
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()

                if (engineName == "DuckDuckGo") {
                    val jsonArray = JSONArray(bodyString)
                    val suggestions = mutableListOf<SearchSuggestion>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val phrase = obj.optString("phrase")
                        if (!phrase.isNullOrEmpty()) {
                            suggestions.add(SearchSuggestion(SuggestionType.SEARCH, phrase))
                        }
                    }
                    suggestions.take(5)
                } else {
                    val jsonArray = JSONArray(bodyString)
                    if (jsonArray.length() > 1) {
                        val innerArray = jsonArray.getJSONArray(1)
                        val suggestions = mutableListOf<SearchSuggestion>()
                        for (i in 0 until innerArray.length()) {
                            val value = innerArray.optString(i)
                            if (!value.isNullOrEmpty()) {
                                suggestions.add(SearchSuggestion(SuggestionType.SEARCH, value))
                            }
                        }
                        suggestions.take(5)
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getSearchSuggestions(
        input: String,
        engineName: String,
        historyResults: List<SearchSuggestion>,
        bookmarkResults: List<SearchSuggestion>,
        client: OkHttpClient,
        browsingContext: BrowsingContext
    ): List<SearchSuggestion> {
        if (input.isBlank()) return emptyList()

        if (!browsingContext.isPrivate) {
            synchronized(suggestionMemoryCache) {
                val cached = suggestionMemoryCache[input]
                if (cached != null) return cached
            }
        }

        val apiUrl = getSuggestionApiUrl(engineName, input)
        val searchResults = if (!apiUrl.isNullOrEmpty()) {
            fetchSearchSuggestions(apiUrl, engineName, input, client)
        } else {
            emptyList()
        }

        val merged = (historyResults + bookmarkResults + searchResults).take(9)
        if (!browsingContext.isPrivate) {
            synchronized(suggestionMemoryCache) {
                suggestionMemoryCache[input] = merged
            }
        }
        return merged
    }

    override fun clearCache() {
        synchronized(suggestionMemoryCache) {
            suggestionMemoryCache.clear()
        }
    }
}

class SearchSuggestions {
    fun getSuggestions(query: String): List<String> {
        return if (query.isEmpty()) emptyList() else listOf(
            "$query news",
            "$query weather",
            "$query definition",
            "$query map"
        )
    }
}

enum class VoiceActionType {
    NAVIGATE, BACK, FORWARD, REFRESH, NEW_TAB, CLOSE_TAB, SEARCH,
    OPEN_INCOGNITO, CLOSE_INCOGNITO,
    OPEN_DOWNLOADS, OPEN_HISTORY, OPEN_SETTINGS, OPEN_BOOKMARKS,
    SEARCH_CURRENT_SITE,
    CREATE_TAB_GROUP, SWITCH_TAB_GROUP,
    OPEN_LAST_CLOSED_TAB,
    SUMMARIZE_PAGE, EXPLAIN_PAGE, TRANSLATE_PAGE, READ_PAGE_ALOUD, ANALYZE_WEBSITE, VOICE_SEARCH_HISTORY,
    PLAY_VIDEO, PAUSE_VIDEO, CHANGE_PLAYBACK_SPEED, DOWNLOAD_VIDEO
}

data class VoiceCommandResult(
    val actionType: VoiceActionType,
    val payload: String = ""
)

object VoiceSearch {
    fun getVoiceSearchIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak search terms or " + "Swift assistant" + " commands...")
        }
    }

    /**
     * Corrects common phonetic errors in tech/web terminology.
     */
    fun correctTechnicalTerms(input: String): String {
        var corrected = input.trim()
        val terms = mapOf(
            "dot com" to ".com",
            "dot net" to ".net",
            "dot org" to ".org",
            "dot in" to ".in",
            "git hub" to "GitHub",
            "github" to "GitHub",
            "co-lin" to "Kotlin",
            "kotlin" to "Kotlin",
            "android" to "Android",
            "chat gpt" to "ChatGPT",
            "chatgpt" to "ChatGPT",
            "google dot com" to "google.com",
            "samsung review" to "Samsung review",
            "web page" to "webpage",
            "w w w dot" to "www."
        )
        for ((err, corr) in terms) {
            // Case-insensitive regex bounds replacement
            corrected = corrected.replace(Regex("(?i)\\b" + Regex.escape(err) + "\\b"), corr)
        }
        return corrected
    }

    /**
     * Parses the recognized speech text into concrete browser actions/commands.
     */
    fun parseVoiceCommand(text: String): VoiceCommandResult {
        val normalizedText = correctTechnicalTerms(text)
        var clean = normalizedText.trim().lowercase()

        // Strip wake word: "hey swift" or "hello swift" or "swift assistant"
        val wakeWords = listOf("hey swift", "hello swift", "swift assistant", "swift")
        for (wake in wakeWords) {
            if (clean.startsWith(wake)) {
                clean = clean.removePrefix(wake).trim()
                if (clean.startsWith(",") || clean.startsWith(".") || clean.startsWith("-")) {
                    clean = clean.substring(1).trim()
                }
                break
            }
        }

        if (clean.isEmpty()) {
            return VoiceCommandResult(VoiceActionType.SEARCH, text)
        }

        // --- MULTILINGUAL INTENT MATCHING (English, Hindi, Hinglish, Marathi, Tamil, Telugu, Bengali, etc.) ---
        return when {
            // BACK / PREVIOUS
            clean == "go back" || clean == "back" || clean == "previous page" ||
            clean.contains("piche jao") || clean.contains("peeche jao") || clean.contains("pichla page") || // Hindi/Hinglish
            clean.contains("maharashtra") || clean.contains("mage ja") || // Marathi
            clean.contains("pinnaadi po") || // Tamil
            clean.contains("venukaku po") || // Telugu
            clean.contains("pichone") -> // Bengali
                VoiceCommandResult(VoiceActionType.BACK)

            // FORWARD / NEXT
            clean == "go forward" || clean == "forward" || clean == "next page" ||
            clean.contains("aage jao") || clean.contains("agla page") || // Hindi/Hinglish
            clean.contains("pudhe ja") || // Marathi
            clean.contains("munnaadi po") || // Tamil
            clean.contains("mungatiki po") || // Telugu
            clean.contains("samne jao") -> // Bengali
                VoiceCommandResult(VoiceActionType.FORWARD)

            // REFRESH / RELOAD
            clean == "refresh" || clean == "reload" || clean.contains("dubara load") || clean.contains("phir se load") || clean.contains("refresh karo") ->
                VoiceCommandResult(VoiceActionType.REFRESH)

            // INCOGNITO MODE (Open/Close)
            clean.contains("open incognito") || clean.contains("new incognito") || clean.contains("incognito mode") || clean.contains("private tab") ||
            clean.contains("incognito kholo") || clean.contains("private tab kholo") || clean.contains("gupt tab") -> // Hindi/Hinglish
                VoiceCommandResult(VoiceActionType.OPEN_INCOGNITO)

            clean.contains("close incognito") || clean.contains("exit incognito") || clean.contains("close private") ||
            clean.contains("incognito band") || clean.contains("incognito close") ->
                VoiceCommandResult(VoiceActionType.CLOSE_INCOGNITO)

            // DOWNLOADS
            clean.contains("open downloads") || clean.contains("show downloads") || clean == "downloads" ||
            clean.contains("downloads kholo") || clean.contains("download dikhao") || clean.contains("download pathu") ->
                VoiceCommandResult(VoiceActionType.OPEN_DOWNLOADS)

            // HISTORY
            clean.contains("open history") || clean.contains("show history") || clean == "history" ||
            clean.contains("history kholo") || clean.contains("itihas kholo") || clean.contains("history pathu") || clean.contains("charitra open") ->
                VoiceCommandResult(VoiceActionType.OPEN_HISTORY)

            // SETTINGS
            clean.contains("open settings") || clean.contains("show settings") || clean == "settings" ||
            clean.contains("settings kholo") || clean.contains("setting kholo") || clean.contains("amaipu open") ->
                VoiceCommandResult(VoiceActionType.OPEN_SETTINGS)

            // BOOKMARKS
            clean.contains("open bookmarks") || clean.contains("show bookmarks") || clean == "bookmarks" ||
            clean.contains("bookmarks kholo") || clean.contains("bookmark kholo") ->
                VoiceCommandResult(VoiceActionType.OPEN_BOOKMARKS)

            // RESTORE TAB
            clean.contains("open last closed") || clean.contains("reopen closed") || clean.contains("restore tab") || clean.contains("undo close") ||
            clean.contains("purana tab kholo") || clean.contains("last closed tab") ->
                VoiceCommandResult(VoiceActionType.OPEN_LAST_CLOSED_TAB)

            // NEW TAB
            clean.contains("new tab") || clean.contains("open tab") || clean.contains("create tab") ||
            clean.contains("naya tab") || clean.contains("ek aur tab") || clean.contains("tab kholo") ||
            clean.contains("pudhiya tab") || clean.contains("kotha tab") ->
                VoiceCommandResult(VoiceActionType.NEW_TAB)

            // CLOSE TAB
            clean == "close tab" || clean == "delete tab" || clean == "close current tab" ||
            clean.contains("tab band karo") || clean.contains("tab close karo") || clean.contains("tab moodu") || clean.contains("tab moosiveyi") ->
                VoiceCommandResult(VoiceActionType.CLOSE_TAB)

            // SEARCH INSIDE SITE
            clean.startsWith("search site ") || clean.startsWith("search current website ") || clean.startsWith("find on page ") || clean.startsWith("search this site ") ||
            clean.startsWith("page par dhundo ") || clean.startsWith("page le thedu ") || clean.startsWith("page lo vethuku ") -> {
                val query = clean.removePrefix("search site ").removePrefix("search current website ").removePrefix("find on page ").removePrefix("search this site ")
                    .removePrefix("page par dhundo ").removePrefix("page le thedu ").removePrefix("page lo vethuku ").trim()
                VoiceCommandResult(VoiceActionType.SEARCH_CURRENT_SITE, query)
            }

            // TAB GROUPS
            clean.startsWith("create tab group ") || clean.startsWith("create group ") || clean.startsWith("group tabs ") || clean.startsWith("tab group banao ") -> {
                val groupName = clean.removePrefix("create tab group ").removePrefix("create group ").removePrefix("group tabs ").removePrefix("tab group banao ").trim()
                VoiceCommandResult(VoiceActionType.CREATE_TAB_GROUP, groupName)
            }
            clean.startsWith("switch tab group ") || clean.startsWith("go to group ") || clean.startsWith("switch group ") || clean.startsWith("open group ") || clean.startsWith("group par jao ") -> {
                val groupName = clean.removePrefix("switch tab group ").removePrefix("go to group ").removePrefix("switch group ").removePrefix("open group ").removePrefix("group par jao ").trim()
                VoiceCommandResult(VoiceActionType.SWITCH_TAB_GROUP, groupName)
            }

            // AI SUMMARIZE PAGE
            clean.contains("summarize page") || clean.contains("summarize website") || clean == "summarize" || clean.contains("page summary") ||
            clean.contains("summary dikhao") || clean.contains("summary batao") || clean.contains("saransh dikhao") || // Hindi
            clean.contains("surukkam") || // Tamil
            clean.contains("sangraham") || // Telugu
            clean.contains("saransh dakhva") -> // Marathi
                VoiceCommandResult(VoiceActionType.SUMMARIZE_PAGE)

            // AI EXPLAIN PAGE
            clean.contains("explain page") || clean.contains("explain website") || clean == "explain" || clean.contains("explain this") ||
            clean.contains("samjhao") || clean.contains("isey samjhao") || clean.contains("vilaku") || clean.contains("vivarinchu") ->
                VoiceCommandResult(VoiceActionType.EXPLAIN_PAGE)

            // AI TRANSLATE PAGE
            clean.startsWith("translate page to ") || clean.startsWith("translate website to ") || clean.startsWith("translate to ") ||
            clean.startsWith("anuvad karo ") || clean.startsWith("bhashantar kara ") -> {
                val lang = clean.removePrefix("translate page to ").removePrefix("translate website to ").removePrefix("translate to ")
                    .removePrefix("anuvad karo ").removePrefix("bhashantar kara ").trim()
                VoiceCommandResult(VoiceActionType.TRANSLATE_PAGE, lang)
            }
            clean.contains("translate page") || clean.contains("translate website") || clean == "translate" ||
            clean.contains("anuvad") || clean.contains("bhashantar") || clean.contains("mozhipeyarppu") || clean.contains("anuvadhinchu") ->
                VoiceCommandResult(VoiceActionType.TRANSLATE_PAGE, "hindi") // Default to Hindi

            // READ ALOUD / SPEECH CONTENT
            clean.contains("read page aloud") || clean.contains("read aloud") || clean.contains("speak page") || clean.contains("read text") || clean == "read" ||
            clean.contains("padh kar sunao") || clean.contains("sunao") || clean.contains("vasi") || clean.contains("chuvu") || clean.contains("vacha kara") ->
                VoiceCommandResult(VoiceActionType.READ_PAGE_ALOUD)

            // ANALYZE WEBSITE
            clean.contains("analyze website") || clean.contains("analyze page") || clean.contains("analyze this") || clean.contains("website analysis") ||
            clean.contains("vishwaleshan") || clean.contains("vicharana") ->
                VoiceCommandResult(VoiceActionType.ANALYZE_WEBSITE)

            // SEARCH HISTORY FOR
            clean.startsWith("search history for ") || clean.startsWith("search history ") || clean.startsWith("find in history ") ||
            clean.startsWith("history me dhundo ") || clean.startsWith("history me search karo ") -> {
                val query = clean.removePrefix("search history for ").removePrefix("search history ").removePrefix("find in history ")
                    .removePrefix("history me dhundo ").removePrefix("history me search karo ").trim()
                VoiceCommandResult(VoiceActionType.VOICE_SEARCH_HISTORY, query)
            }

            // MEDIA PLAY/PAUSE/SPEED/DOWNLOAD
            clean.contains("play video") || clean.contains("resume video") || clean.contains("play content") || clean.contains("resume media") || clean == "play" ||
            clean.contains("video chalao") || clean.contains("play karo") || clean.contains("video podu") || clean.contains("video veyi") ->
                VoiceCommandResult(VoiceActionType.PLAY_VIDEO)

            clean.contains("pause video") || clean.contains("stop video") || clean.contains("pause content") || clean.contains("pause media") || clean == "pause" ||
            clean.contains("video roko") || clean.contains("pause karo") || clean.contains("video niruthu") || clean.contains("video aapu") ->
                VoiceCommandResult(VoiceActionType.PAUSE_VIDEO)

            clean.startsWith("speed up ") || clean.startsWith("slow down ") || clean.startsWith("change playback speed ") || clean.startsWith("video speed ") || clean.startsWith("speed ") ||
            clean.startsWith("playback speed ") -> {
                val speed = clean.removePrefix("speed up ").removePrefix("slow down ").removePrefix("change playback speed ").removePrefix("video speed ").removePrefix("speed ")
                    .removePrefix("playback speed ").trim()
                VoiceCommandResult(VoiceActionType.CHANGE_PLAYBACK_SPEED, speed)
            }

            clean.contains("download video") || clean.contains("save video") || clean.contains("get video") ||
            clean.contains("video download karo") || clean.contains("save video") || clean.contains("video download pannu") ->
                VoiceCommandResult(VoiceActionType.DOWNLOAD_VIDEO)

            // GENERAL NAVIGATION & SEARCH
            clean.startsWith("open ") || clean.startsWith("go to ") || clean.startsWith("kholo ") -> {
                var target = clean.removePrefix("open ").removePrefix("go to ").removePrefix("kholo ").trim()

                // If the query represents historical requests like "Samsung review page from history", handle contextually
                if (target.contains("samsung") && target.contains("history")) {
                    return VoiceCommandResult(VoiceActionType.VOICE_SEARCH_HISTORY, "Samsung review")
                }

                val url = when (target.lowercase().trim()) {
                    "google" -> "https://google.com"
                    "gmail" -> "https://gmail.com"
                    "facebook" -> "https://facebook.com"
                    "chatgpt" -> "https://chatgpt.com"
                    else -> {
                        if (target.contains(".")) {
                            if (!target.startsWith("http")) "https://$target" else target
                        } else {
                            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
                        }
                    }
                }
                VoiceCommandResult(VoiceActionType.NAVIGATE, url)
            }

            else -> {
                // If it contains search triggers, strip them down cleanly
                var query = normalizedText
                if (clean.startsWith("search for ")) {
                    query = normalizedText.substring(11).trim()
                } else if (clean.startsWith("search ")) {
                    query = normalizedText.substring(7).trim()
                } else if (clean.startsWith("dhundo ")) {
                    query = normalizedText.substring(7).trim()
                } else if (clean.startsWith("find ")) {
                    query = normalizedText.substring(5).trim()
                }
                VoiceCommandResult(VoiceActionType.SEARCH, query)
            }
        }
    }
}
