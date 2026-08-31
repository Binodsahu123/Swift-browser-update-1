package com.swift.browser.aiengine

import org.json.JSONArray
import org.json.JSONObject

data class WebsiteContext(
    val title: String = "",
    val headings: List<String> = emptyList(),
    val subheadings: List<String> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    val lists: List<String> = emptyList(),
    val tables: List<String> = emptyList(),
    val importantTexts: List<String> = emptyList(),
    val links: List<Pair<String, String>> = emptyList()
) {
    fun toFormattedPromptString(): String {
        val sb = StringBuilder()
        sb.append("Title: $title\n\n")
        
        if (headings.isNotEmpty()) {
            sb.append("Headings:\n")
            headings.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        
        if (subheadings.isNotEmpty()) {
            sb.append("Subheadings:\n")
            subheadings.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        
        if (paragraphs.isNotEmpty()) {
            sb.append("Paragraphs:\n")
            paragraphs.forEach { sb.append("$it\n") }
            sb.append("\n")
        }
        
        if (lists.isNotEmpty()) {
            sb.append("Lists/Points:\n")
            lists.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        
        if (tables.isNotEmpty()) {
            sb.append("Tables:\n")
            tables.forEach { sb.append("$it\n\n") }
        }
        
        if (importantTexts.isNotEmpty()) {
            sb.append("Key Highlighting/Important Words:\n")
            importantTexts.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }

        if (links.isNotEmpty()) {
            sb.append("Hyperlinks on Page:\n")
            links.forEach { (text, href) -> sb.append("- $text: $href\n") }
            sb.append("\n")
        }
        
        return sb.toString()
    }
}

object WebsiteContextExtractor {
    const val JS_EXTRACT_SCRIPT = """
        (function() {
            try {
                var data = {
                    title: document.title || '',
                    headings: Array.from(document.querySelectorAll('h1, h2, h3')).map(e => e.innerText.trim()).filter(Boolean).slice(0, 15),
                    subheadings: Array.from(document.querySelectorAll('h4, h5, h6')).map(e => e.innerText.trim()).filter(Boolean).slice(0, 15),
                    paragraphs: Array.from(document.querySelectorAll('p')).map(e => e.innerText.trim()).filter(Boolean).slice(0, 20),
                    lists: Array.from(document.querySelectorAll('ul li, ol li')).map(e => e.innerText.trim()).filter(Boolean).slice(0, 20),
                    tables: Array.from(document.querySelectorAll('table')).map(t => Array.from(t.querySelectorAll('tr')).map(r => Array.from(r.querySelectorAll('td, th')).map(c => c.innerText.trim()).join(' | ')).join('\n')).filter(Boolean).slice(0, 5),
                    importantTexts: Array.from(document.querySelectorAll('strong, b, em')).map(e => e.innerText.trim()).filter(Boolean).slice(0, 15),
                    links: Array.from(document.querySelectorAll('a')).map(e => { return { text: e.innerText.trim(), href: e.href } }).filter(e => e.text && e.href && e.href.startsWith('http')).slice(0, 15)
                };
                return JSON.stringify(data);
            } catch(err) {
                return JSON.stringify({error: err.message, title: document.title || 'Error'});
            }
        })()
    """

    fun parseJsonToContext(jsonStr: String): WebsiteContext {
        if (jsonStr.isBlank() || jsonStr == "null" || jsonStr == "undefined") {
            return WebsiteContext()
        }
        try {
            // Clean dynamic string wrap if returned by script engine
            val raw = if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"") && jsonStr.length > 2) {
                try {
                    org.json.JSONTokener(jsonStr).nextValue() as? String ?: jsonStr
                } catch (e: Exception) {
                    jsonStr
                }
            } else {
                jsonStr
            }
            val json = JSONObject(raw)
            
            val title = json.optString("title", "")
            
            val headings = mutableListOf<String>()
            json.optJSONArray("headings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    headings.add(arr.getString(i))
                }
            }

            val subheadings = mutableListOf<String>()
            json.optJSONArray("subheadings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    subheadings.add(arr.getString(i))
                }
            }

            val paragraphs = mutableListOf<String>()
            json.optJSONArray("paragraphs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    paragraphs.add(arr.getString(i))
                }
            }

            val lists = mutableListOf<String>()
            json.optJSONArray("lists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    lists.add(arr.getString(i))
                }
            }

            val tables = mutableListOf<String>()
            json.optJSONArray("tables")?.let { arr ->
                for (i in 0 until arr.length()) {
                    tables.add(arr.getString(i))
                }
            }

            val importantTexts = mutableListOf<String>()
            json.optJSONArray("importantTexts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    importantTexts.add(arr.getString(i))
                }
            }

            val links = mutableListOf<Pair<String, String>>()
            json.optJSONArray("links")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val text = item.optString("text", "")
                    val href = item.optString("href", "")
                    if (text.isNotBlank() && href.isNotBlank()) {
                        links.add(Pair(text, href))
                    }
                }
            }

            return WebsiteContext(
                title = title,
                headings = headings,
                subheadings = subheadings,
                paragraphs = paragraphs,
                lists = lists,
                tables = tables,
                importantTexts = importantTexts,
                links = links
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return WebsiteContext(title = "Fallback Content", paragraphs = listOf(jsonStr))
        }
    }
}
