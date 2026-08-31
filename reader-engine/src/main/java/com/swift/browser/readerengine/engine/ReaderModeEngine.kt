package com.swift.browser.readerengine.engine

import android.webkit.WebView
import com.swift.browser.readerengine.api.ReaderEngineApi
import com.swift.browser.readerengine.model.ReaderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

class ReaderModeEngine : ReaderEngineApi {
    private val _readerState = MutableStateFlow(ReaderState())
    override val readerState: StateFlow<ReaderState> = _readerState.asStateFlow()

    override fun detectReaderModeAvailability(webView: WebView, tabId: String) {
        webView.evaluateJavascript(
            """
            (function() {
                var article = document.querySelector('article');
                if (article) return true;
                
                var paras = document.querySelectorAll('p');
                var wordCount = 0;
                paras.forEach(p => wordCount += (p.innerText ? p.innerText.split(/\s+/).length : 0));
                return (paras.length >= 3 && wordCount > 350);
            })()
            """.trimIndent()
        ) { result ->
            val isAvailable = result?.toBoolean() ?: false
            val webUrl = webView.url ?: ""
            val isHttpOrHttps = webUrl.startsWith("http://", ignoreCase = true) || webUrl.startsWith("https://", ignoreCase = true)
            val finalAvailable = isAvailable || isHttpOrHttps
            _readerState.update { state ->
                
                state.copy(isAvailable = finalAvailable)
            }
        }
    }

    override fun triggerReaderMode(webView: WebView, tabId: String) {
        webView.evaluateJavascript(
            """
            (function() {
                var title = "";
                var h1 = document.querySelector('h1');
                if (h1) title = h1.innerText;
                if (!title) title = document.title || "";
                
                var author = "";
                var metaAuthor = document.querySelector('meta[name="author"]');
                if (metaAuthor) author = metaAuthor.getAttribute('content');
                if (!author) {
                    var authorEl = document.querySelector("[class*='author'], [id*='author'], [rel='author']");
                    if (authorEl) author = authorEl.innerText;
                }
                
                var date = "";
                var metaDate = document.querySelector('meta[property="article:published_time"], meta[name="publish-date"], meta[name="pubdate"]');
                if (metaDate) date = metaDate.getAttribute('content');
                if (!date) {
                    var timeEl = document.querySelector('time');
                    if (timeEl) date = timeEl.innerText || timeEl.getAttribute('datetime');
                }
                if (!date) {
                    var dateEl = document.querySelector("[class*='date'], [class*='publish'], [id*='date']");
                    if (dateEl) date = dateEl.innerText;
                }
                var origin = window.location.origin || "";
                var domain = window.location.hostname || "";
                
                var container = document.querySelector('article');
                if (!container) {
                    var bestParent = null;
                    var maxPCount = 0;
                    var parents = Array.from(document.querySelectorAll('p')).map(p => p.parentElement);
                    var uniqueParents = Array.from(new Set(parents));
                    uniqueParents.forEach(p => {
                        if (!p) return;
                        var count = p.querySelectorAll('p').length;
                        if (count > maxPCount) {
                            maxPCount = count;
                            bestParent = p;
                        }
                    });
                    if (bestParent && maxPCount >= 2) container = bestParent;
                }
                
                var extractedHtml = "";
                if (container) {
                    var clone = container.cloneNode(true);
                    var selector = "script, style, nav, footer, header, form, iframe, .sidebar, .ads, [class*='ads'], [id*='ads'], [class*='promo'], [id*='promo'], [class*='widget'], [id*='widget'], [class*='share'], [id*='share'], [class*='cookie'], [class*='popup'], [class*='social']";
                    var toRemove = clone.querySelectorAll(selector);
                    toRemove.forEach(el => el.remove());
                    
                    var imgs = clone.querySelectorAll('img');
                    imgs.forEach(img => {
                        var src = img.getAttribute('src');
                        var dataSrc = img.getAttribute('data-src') || img.getAttribute('data-original');
                        if (dataSrc) src = dataSrc;
                        if (src) {
                            if (src.startsWith('//')) {
                                img.setAttribute('src', 'https:' + src);
                            } else if (src.startsWith('/')) {
                                img.setAttribute('src', origin + src);
                            } else if (!src.startsWith('http')) {
                                var path = window.location.pathname;
                                var basePath = path.substring(0, path.lastIndexOf('/') + 1);
                                img.setAttribute('src', origin + basePath + src);
                            } else {
                                img.setAttribute('src', src);
                            }
                            img.style.maxWidth = "100%";
                            img.style.height = "auto";
                            img.style.borderRadius = "8px";
                            img.style.margin = "12px 0";
                            img.style.display = "block";
                        } else {
                            img.remove();
                        }
                    });
                    
                    extractedHtml = clone.innerHTML;
                } else {
                    var paras = Array.from(document.querySelectorAll('p')).map(p => p.outerHTML);
                    extractedHtml = paras.join("");
                }
                
                if (!extractedHtml || extractedHtml.trim().length < 50) {
                    var divs = Array.from(document.querySelectorAll('div')).filter(function(d) {
                        var txt = d.innerText || "";
                        return txt.trim().split(/\s+/).length > 50 && d.querySelectorAll('div').length < 3;
                    });
                    if (divs.length > 0) {
                        extractedHtml = divs.map(function(d) { return "<p>" + (d.innerText || "") + "</p>"; }).join("");
                    }
                }
                if (!extractedHtml || extractedHtml.trim().length < 50) {
                    var bodyText = document.body ? (document.body.innerText || "") : "";
                    var lines = bodyText.split(/\n\n+/).filter(function(l) { return l.trim().length > 30; });
                    extractedHtml = lines.map(function(l) { return "<p>" + l.trim() + "</p>"; }).join("");
                }
                
                var payload = JSON.stringify({
                    title: title,
                    author: author,
                    date: date,
                    domain: domain,
                    html: extractedHtml
                });
                try {
                    return btoa(unescape(encodeURIComponent(payload)));
                } catch(e) {
                    return btoa(unescape(encodeURIComponent(JSON.stringify({
                        title: title,
                        author: author,
                        date: date,
                        domain: domain,
                        html: "Error encoding content: " + e.message
                    }))));
                }
            })()
            """.trimIndent()
        ) { b64Result ->
            try {
                var cleaned = b64Result ?: ""
                if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2) {
                    cleaned = cleaned.substring(1, cleaned.length - 1)
                }
                val decodedBytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
                val decodedString = String(decodedBytes, Charsets.UTF_8)
                
                val jsonObject = JSONObject(decodedString)
                val extractedTitle = jsonObject.optString("title", "Article")
                val extractedAuthor = jsonObject.optString("author", "")
                val extractedDate = jsonObject.optString("date", "")
                val extractedDomain = jsonObject.optString("domain", "")
                val extractedHtml = jsonObject.optString("html", "No text content found.")
                
                _readerState.update {
                    it.copy(
                        title = extractedTitle,
                        author = extractedAuthor.ifBlank { null },
                        date = extractedDate.ifBlank { null },
                        domain = extractedDomain.ifBlank { null },
                        content = extractedHtml,
                        isActive = true
                    )
                }
            } catch (e: Exception) {
                _readerState.update {
                    it.copy(
                        title = "Article Reader",
                        content = "Failed to parse content cleanly: ${e.localizedMessage}. Please read the web version.",
                        isActive = true
                    )
                }
            }
        }
    }

    override fun closeReaderMode() {
        _readerState.update { it.copy(isActive = false) }
    }

    override fun updateReaderFontSize(size: Int) {
        _readerState.update { it.copy(fontSize = size) }
    }

    override fun updateReaderTypeface(isSerif: Boolean) {
        _readerState.update { it.copy(isSerif = isSerif) }
    }

    override fun updateReaderTheme(theme: String) {
        _readerState.update { it.copy(theme = theme) }
    }
    
    companion object {
        @Volatile
        private var instance: ReaderModeEngine? = null

        fun getInstance(): ReaderModeEngine {
            return instance ?: synchronized(this) {
                instance ?: ReaderModeEngine().also { instance = it }
            }
        }
    }
}
// trigger rebuild
