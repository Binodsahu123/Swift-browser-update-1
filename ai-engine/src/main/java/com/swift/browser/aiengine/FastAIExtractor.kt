package com.swift.browser.aiengine

import org.json.JSONObject

data class FastPageContext(
    val title: String = "",
    val headings: List<String> = emptyList(),
    val paragraphs: List<String> = emptyList()
) {
    fun toFormattedPromptString(): String {
        val sb = StringBuilder()
        sb.append("Title: $title\n\n")
        if (headings.isNotEmpty()) {
            sb.append("Headings:\n")
            headings.forEach { sb.append("- $it\n") }
            sb.append("\n")
        }
        if (paragraphs.isNotEmpty()) {
            sb.append("Content:\n")
            paragraphs.forEach { sb.append("$it\n") }
        }
        return sb.toString()
    }
}

object FastAIExtractor {
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
                
                var headings = Array.from(document.querySelectorAll('h1, h2'))
                    .filter(function(e) { return !isExcluded(e); })
                    .map(function(e) { return e.innerText.trim(); })
                    .filter(Boolean)
                    .slice(0, 15);
                    
                var paragraphs = [];
                var allParagraphElements = document.querySelectorAll('p, article p');
                var totalChars = 0;
                var maxParagraphs = 50;
                var maxChars = 20000;
                
                for (var i = 0; i < allParagraphElements.length && paragraphs.length < maxParagraphs && totalChars < maxChars; i++) {
                    var el = allParagraphElements[i];
                    if (!isExcluded(el)) {
                        var text = el.innerText.trim();
                        if (text.length > 25) {
                            if (totalChars + text.length > maxChars) {
                                text = text.substring(0, maxChars - totalChars);
                            }
                            if (text.length > 0) {
                                paragraphs.push(text);
                                totalChars += text.length;
                            }
                        }
                    }
                }
                
                var data = {
                    title: title,
                    headings: headings,
                    paragraphs: paragraphs
                };
                return JSON.stringify(data);
            } catch(err) {
                return JSON.stringify({error: err.message, title: document.title || 'Error', headings: [], paragraphs: []});
            }
        })()
    """

    fun parseJsonToContext(jsonStr: String): FastPageContext {
        if (jsonStr.isBlank() || jsonStr == "null" || jsonStr == "undefined") {
            return FastPageContext()
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
            
            val headings = mutableListOf<String>()
            json.optJSONArray("headings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    headings.add(arr.getString(i))
                }
            }
            
            val paragraphs = mutableListOf<String>()
            json.optJSONArray("paragraphs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    paragraphs.add(arr.getString(i))
                }
            }
            
            return FastPageContext(title, headings, paragraphs)
        } catch (e: Exception) {
            return FastPageContext(title = "Fallback", paragraphs = listOf(jsonStr))
        }
    }
}
