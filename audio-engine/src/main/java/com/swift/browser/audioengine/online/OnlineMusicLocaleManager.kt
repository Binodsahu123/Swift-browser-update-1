package com.swift.browser.audioengine.online

import android.content.Context
import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class OnlineMusicLocale(
    val language: String,
    val country: String,
    val languageTag: String
)

object OnlineMusicLocaleManager {
    private const val PREFS_NAME = "swift_browser_music_locale_prefs"
    private const val KEY_USER_LANGUAGE = "user_preferred_music_language"
    private const val KEY_GEO_COUNTRY = "geo_country_code"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getUserLanguage(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_LANGUAGE, null)
    }

    fun setUserLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().putString(KEY_USER_LANGUAGE, languageCode).apply()
    }

    fun fetchGeoCountryCode(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_GEO_COUNTRY)) return

        Thread {
            try {
                val url = URL("https://get.geojs.io/v1/ip/geo.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val match = Regex("\"country_code\"\\s*:\\s*\"([A-Z]{2})\"").find(text)
                    val country = match?.groupValues?.get(1)
                    if (!country.isNullOrEmpty()) {
                        prefs.edit().putString(KEY_GEO_COUNTRY, country.uppercase(Locale.ROOT)).apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun resolveLocale(context: Context): OnlineMusicLocale {
        fetchGeoCountryCode(context) // best-effort async fetch for next sessions

        val prefs = getPrefs(context)
        val userLang = getUserLanguage(context)
        val defaultLocale = Locale.getDefault()
        val geoCountry = prefs.getString(KEY_GEO_COUNTRY, null)
        val defaultCountry = (geoCountry ?: defaultLocale.country).ifEmpty { "US" }.uppercase(Locale.ROOT)

        val language = userLang ?: run {
            val deviceLang = defaultLocale.language.lowercase(Locale.ROOT)
            if (defaultCountry == "IN") {
                val supportedIndianLangs = listOf("hi", "mr", "gu", "ta", "te", "kn", "ml", "bn", "pa", "or", "as", "en")
                if (deviceLang in supportedIndianLangs) {
                    deviceLang
                } else {
                    "hi"
                }
            } else {
                languageForCountry(defaultCountry)
            }
        }

        val tag = "$language-$defaultCountry"
        return OnlineMusicLocale(language, defaultCountry, tag)
    }

    fun languageForCountry(countryCode: String): String {
        return when (countryCode.uppercase(Locale.ROOT)) {
            "IN" -> "hi"
            "US", "GB", "CA", "AU", "NZ", "IE" -> "en"
            "ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "DO", "BO", "HN", "PY", "SV", "NI", "CR", "PR" -> "es"
            "FR", "BE", "LU", "MC" -> "fr"
            "DE", "AT", "LI", "CH" -> "de"
            "BR", "PT", "AO", "MZ" -> "pt"
            "JP" -> "ja"
            "KR" -> "ko"
            "RU", "BY", "KZ", "KG" -> "ru"
            "SA", "AE", "EG", "IQ", "DZ", "MA", "SD", "SY", "YE", "TN", "JO", "LY", "LB", "OM", "KW", "QA", "BH" -> "ar"
            "IT" -> "it"
            "TR" -> "tr"
            "ID" -> "id"
            "VN" -> "vi"
            "TH" -> "th"
            "NL" -> "nl"
            "PL" -> "pl"
            "UA" -> "uk"
            "SE" -> "sv"
            "NO" -> "no"
            "FI" -> "fi"
            "DK" -> "da"
            "PH" -> "tl"
            "MY" -> "ms"
            "PK" -> "ur"
            "BD" -> "bn"
            "CN", "TW", "HK" -> "zh"
            else -> "en"
        }
    }

    fun detectQueryLanguage(query: String): String {
        val q = query.lowercase(Locale.ROOT).trim()

        // Script checks
        if (q.any { it in '\u0900'..'\u097F' }) return "hi"
        if (q.any { it in '\u0980'..'\u09FF' }) return "bn"
        if (q.any { it in '\u0A00'..'\u0A7F' }) return "pa"
        if (q.any { it in '\u0A80'..'\u0AFF' }) return "gu"
        if (q.any { it in '\u0B80'..'\u0BFF' }) return "ta"
        if (q.any { it in '\u0C00'..'\u0C7F' }) return "te"
        if (q.any { it in '\u0C80'..'\u0CFF' }) return "kn"
        if (q.any { it in '\u0D00'..'\u0D7F' }) return "ml"
        if (q.any { it in '\u0600'..'\u06FF' }) return "ar"
        if (q.any { it in '\u0400'..'\u04FF' }) return "ru"
        if (q.any { it in '\uAC00'..'\uD7AF' }) return "ko"
        if (q.any { it in '\u3040'..'\u30FF' }) return "ja"
        if (q.any { it in '\u4E00'..'\u9FFF' }) return "zh"

        // Keyword checks
        if (containsAnyKeyword(q, listOf("hindi", "bhojpuri", "haryanvi", "bhajan", "ghazal", "bollywood", "gaana"))) return "hi"
        if (containsAnyKeyword(q, listOf("punjabi", "shabad", "bhangra"))) return "pa"
        if (containsAnyKeyword(q, listOf("tamil", "kollywood"))) return "ta"
        if (containsAnyKeyword(q, listOf("telugu", "tollywood"))) return "te"
        if (containsAnyKeyword(q, listOf("kannada", "sandalwood"))) return "kn"
        if (containsAnyKeyword(q, listOf("malayalam", "mollywood"))) return "ml"
        if (containsAnyKeyword(q, listOf("bengali", "bangla"))) return "bn"
        if (containsAnyKeyword(q, listOf("gujarati", "garba"))) return "gu"
        if (containsAnyKeyword(q, listOf("marathi", "lavani"))) return "mr"
        if (containsAnyKeyword(q, listOf("korean", "kpop", "bts", "blackpink", "hangul"))) return "ko"
        if (containsAnyKeyword(q, listOf("japanese", "jpop", "anime", "otaku"))) return "ja"
        if (containsAnyKeyword(q, listOf("spanish", "latino", "reggaeton", "salsa"))) return "es"
        if (containsAnyKeyword(q, listOf("russian", "cyrillic", "hardbass"))) return "ru"
        if (containsAnyKeyword(q, listOf("arabic", "nasheed"))) return "ar"
        if (containsAnyKeyword(q, listOf("french", "chanson"))) return "fr"
        if (containsAnyKeyword(q, listOf("german", "schlager", "rammstein"))) return "de"
        if (containsAnyKeyword(q, listOf("portuguese", "fado", "bossa"))) return "pt"
        if (containsAnyKeyword(q, listOf("turkish", "tpop"))) return "tr"
        if (containsAnyKeyword(q, listOf("english", "hollywood", "pop", "rock", "jazz"))) return "en"

        return "en"
    }

    private fun containsAnyKeyword(query: String, keywords: List<String>): Boolean {
        return keywords.any { query.contains(it) }
    }

    fun getHomeUrl(context: Context? = null): String {
        val defaultLocale = Locale.getDefault()
        var language = defaultLocale.language.ifEmpty { "en" }
        var country = defaultLocale.country.ifEmpty { "US" }

        if (context != null) {
            val resolved = resolveLocale(context)
            language = resolved.language
            country = resolved.country
        }

        return "https://soundcloud.com/?hl=$language&gl=$country"
    }

    fun getSearchUrl(query: String, context: Context? = null): String {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }

        var language = detectQueryLanguage(query)
        if (context != null) {
            if (language != "en") {
                setUserLanguage(context, language)
            } else {
                language = resolveLocale(context).language
            }
        }

        val country = if (context != null) resolveLocale(context).country else Locale.getDefault().country.ifEmpty { "US" }

        return "https://soundcloud.com/search?q=$encodedQuery&hl=$language&gl=$country"
    }
}
