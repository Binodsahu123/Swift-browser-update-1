package com.swift.browser.newsengine

import android.util.Xml

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.security.MessageDigest

object RssFeedParser {
    fun fetchAndParseRss(
        client: OkHttpClient,
        url: String,
        category: String
    ): List<NewsItemEntity> {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .build()
        val list = mutableListOf<NewsItemEntity>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val xmlData = response.body?.string() ?: return emptyList()
                
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xmlData))
                var eventType = parser.eventType
                
                var currentItemTitle = ""
                var currentItemLink = ""
                var currentItemPubDate = ""
                var currentItemDesc = ""
                var currentItemImg = ""
                var currentItemSource = ""
                
                var inItem = false
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (name.equals("item", ignoreCase = true)) {
                                inItem = true
                                currentItemTitle = ""
                                currentItemLink = ""
                                currentItemPubDate = ""
                                currentItemDesc = ""
                                currentItemImg = ""
                                currentItemSource = ""
                            } else if (inItem) {
                                when (name.lowercase()) {
                                    "title" -> currentItemTitle = parser.nextText()
                                    "link" -> currentItemLink = parser.nextText()
                                    "pubdate" -> currentItemPubDate = parser.nextText()
                                    "description" -> {
                                        val rawDesc = parser.nextText() ?: ""
                                        if (currentItemImg.isEmpty()) {
                                            val imgRegex = Regex("<img[^>]+src=(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))", RegexOption.IGNORE_CASE)
                                            val match = imgRegex.find(rawDesc)
                                            if (match != null) {
                                                val rawUrl = (match.groupValues[1].ifBlank { match.groupValues[2].ifBlank { match.groupValues[3] } }).trim()
                                                val url = rawUrl.replace("&amp;", "&")
                                                if (url.isNotEmpty()) {
                                                    currentItemImg = if (url.startsWith("//")) "https:$url" else url
                                                }
                                            }
                                        }
                                        currentItemDesc = stripHtml(rawDesc)
                                    }
                                    "source" -> currentItemSource = parser.nextText()
                                    "enclosure" -> {
                                        var attrUrl: String? = null
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).lowercase() == "url") {
                                                attrUrl = parser.getAttributeValue(i)
                                                break
                                            }
                                        }
                                        if (!attrUrl.isNullOrEmpty()) {
                                            currentItemImg = if (attrUrl.startsWith("//")) "https:$attrUrl" else attrUrl
                                        }
                                    }
                                    "media:content", "content", "media:thumbnail", "thumbnail", "media:image", "image" -> {
                                        var attrUrl: String? = null
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).lowercase() == "url") {
                                                attrUrl = parser.getAttributeValue(i)
                                                break
                                            }
                                        }
                                        if (!attrUrl.isNullOrEmpty()) {
                                            currentItemImg = if (attrUrl.startsWith("//")) "https:$attrUrl" else attrUrl
                                        }
                                    }
                                    "content:encoded", "encoded" -> {
                                        val rawContent = parser.nextText() ?: ""
                                        if (currentItemImg.isEmpty()) {
                                            val imgRegex = Regex("<img[^>]+src=(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))", RegexOption.IGNORE_CASE)
                                            val match = imgRegex.find(rawContent)
                                            if (match != null) {
                                                val rawUrl = (match.groupValues[1].ifBlank { match.groupValues[2].ifBlank { match.groupValues[3] } }).trim()
                                                val url = rawUrl.replace("&amp;", "&")
                                                if (url.isNotEmpty()) {
                                                    currentItemImg = if (url.startsWith("//")) "https:$url" else url
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name.equals("item", ignoreCase = true)) {
                                inItem = false
                                if (currentItemTitle.isNotBlank()) {
                                    val cleanedTitle = cleanTitle(currentItemTitle)
                                    val sourceName = if (currentItemSource.isNotBlank()) {
                                        currentItemSource
                                    } else {
                                        extractSourceFromTitle(currentItemTitle)
                                    }
                                    
                                    val id = md5(currentItemLink.ifBlank { cleanedTitle })
                                    
                                    list.add(
                                        NewsItemEntity(
                                            id = id,
                                            title = cleanedTitle,
                                            description = currentItemDesc.take(200),
                                            imageUrl = currentItemImg,
                                            sourceUrl = currentItemLink,
                                            sourceName = sourceName,
                                            publishedAt = formatPubDate(currentItemPubDate),
                                            category = category,
                                            cachedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "").trim()
    }

    private fun cleanTitle(title: String): String {
        val index = title.lastIndexOf(" - ")
        return if (index != -1) title.substring(0, index).trim() else title.trim()
    }

    private fun extractSourceFromTitle(title: String): String {
        val index = title.lastIndexOf(" - ")
        return if (index != -1) title.substring(index + 3).trim() else "Google News"
    }

    private fun formatPubDate(pubDate: String): String {
        return try {
            if (pubDate.length > 16) pubDate.substring(0, 16) else pubDate
        } catch (e: Exception) {
            pubDate
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
