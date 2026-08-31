package com.swift.browser.aiengine

import org.json.JSONArray
import org.json.JSONObject

data class PriorityContext(
    val title: String = "",
    val metaDescription: String = "",
    val headings: List<String> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    val importantTexts: List<String> = emptyList(),
    val lists: List<String> = emptyList(),
    val tables: List<String> = emptyList()
) {
    fun toFormattedPromptString(): String {
        val sb = StringBuilder()
        sb.append("Title: $title\n")
        if (metaDescription.isNotEmpty()) {
            sb.append("Meta Description: $metaDescription\n")
        }
        sb.append("\n")
        
        if (headings.isNotEmpty()) {
            sb.append("Headings:\n")
            headings.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        
        if (paragraphs.isNotEmpty()) {
            sb.append("Core Contents (Article/Important Paragraphs):\n")
            paragraphs.forEach { sb.append("$it\n") }
            sb.append("\n")
        }
        
        if (lists.isNotEmpty()) {
            sb.append("Bullet Points:\n")
            lists.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        
        if (tables.isNotEmpty()) {
            sb.append("Tables:\n")
            tables.forEach { sb.append("$it\n\n") }
        }
        
        if (importantTexts.isNotEmpty()) {
            sb.append("Emphasized Keywords:\n")
            importantTexts.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        
        return sb.toString()
    }
}

object ContentPriorityExtractor {
    const val JS_EXTRACT_SCRIPT = """
        (function() {
            try {
                function isExcluded(el) {
                    var selectors = [
                        'header', 'footer', 'nav', 'aside', '.sidebar', '.ad', '.advertisement',
                        '#comments', '.comments', '.comment', '#footer', '#header', '#nav',
                        '.nav', '.menu', '.widget', '.tracking', '.analytics', '.social-share',
                        '.banner-ads', '.sponsor', 'iframe', 'noscript'
                    ];
                    return !!el.closest(selectors.join(','));
                }
                
                var title = document.title || '';
                
                var metaDesc = '';
                var meta = document.querySelector('meta[name="description"]') || 
                           document.querySelector('meta[property="og:description"]') ||
                           document.querySelector('meta[name="twitter:description"]');
                if (meta) {
                    metaDesc = (meta.getAttribute('content') || '').trim();
                }
                
                var headings = Array.from(document.querySelectorAll('h1, h2, h3'))
                    .filter(function(e) { return !isExcluded(e); })
                    .map(function(e) { return e.innerText.trim(); })
                    .filter(Boolean)
                    .slice(0, 15);
                    
                var paragraphs = Array.from(document.querySelectorAll('p, article p'))
                    .filter(function(e) { return !isExcluded(e); })
                    .map(function(e) { return e.innerText.trim(); })
                    .filter(function(p) { return p.length > 35; })
                    .slice(0, 25);
                    
                var importantTexts = Array.from(document.querySelectorAll('strong, b, em, mark'))
                    .filter(function(e) { return !isExcluded(e); })
                    .map(function(e) { return e.innerText.trim(); })
                    .filter(Boolean)
                    .slice(0, 20);
                    
                var lists = Array.from(document.querySelectorAll('ul li, ol li'))
                    .filter(function(e) { return !isExcluded(e); })
                    .map(function(e) { return e.innerText.trim(); })
                    .filter(Boolean)
                    .slice(0, 20);
                    
                var tables = Array.from(document.querySelectorAll('table'))
                    .filter(function(e) { return !isExcluded(e); })
                    .map(function(t) {
                        return Array.from(t.querySelectorAll('tr')).map(function(r) {
                            return Array.from(r.querySelectorAll('td, th')).map(function(c) {
                                return c.innerText.trim();
                            }).join(' | ');
                        }).join('\n');
                    })
                    .filter(Boolean)
                    .slice(0, 5);
                    
                var data = {
                    title: title,
                    metaDescription: metaDesc,
                    headings: headings,
                    paragraphs: paragraphs,
                    importantTexts: importantTexts,
                    lists: lists,
                    tables: tables
                };
                return JSON.stringify(data);
            } catch(err) {
                return JSON.stringify({error: err.message, title: document.title || 'Error'});
            }
        })()
    """

    fun parseJsonToPriorityContext(jsonStr: String): PriorityContext {
        if (jsonStr.isBlank() || jsonStr == "null" || jsonStr == "undefined") {
            return PriorityContext()
        }
        try {
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
            val metaDescription = json.optString("metaDescription", "")
            
            val headings = mutableListOf<String>()
            json.optJSONArray("headings")?.let { arr ->
                for (i in 0 until arr.length()) { headings.add(arr.getString(i)) }
            }

            val paragraphs = mutableListOf<String>()
            json.optJSONArray("paragraphs")?.let { arr ->
                for (i in 0 until arr.length()) { paragraphs.add(arr.getString(i)) }
            }

            val importantTexts = mutableListOf<String>()
            json.optJSONArray("importantTexts")?.let { arr ->
                for (i in 0 until arr.length()) { importantTexts.add(arr.getString(i)) }
            }

            val lists = mutableListOf<String>()
            json.optJSONArray("lists")?.let { arr ->
                for (i in 0 until arr.length()) { lists.add(arr.getString(i)) }
            }

            val tables = mutableListOf<String>()
            json.optJSONArray("tables")?.let { arr ->
                for (i in 0 until arr.length()) { tables.add(arr.getString(i)) }
            }

            return PriorityContext(
                title = title,
                metaDescription = metaDescription,
                headings = headings,
                paragraphs = paragraphs,
                importantTexts = importantTexts,
                lists = lists,
                tables = tables
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return PriorityContext(title = "Fallback", paragraphs = listOf(jsonStr))
        }
    }
}
