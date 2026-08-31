package com.swift.browser.translateengine

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

object DomReplacementEngine {

    /**
     * Builds and returns JavaScript to replace extracted node target contents with translated ones.
     */
    fun getReplacementJs(translationsJson: String): String {
        // Escaping backslashes and single quotes in the payload so that it stringifies safely in Javascript
        val escapedJson = translationsJson
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        return """
            (function() {
                var translations = JSON.parse('$escapedJson');
                if (!window.swiftTextNodes) return "no_cache";
                
                var replacedCount = 0;
                translations.forEach(function(item) {
                    var node = window.swiftTextNodes[item.id];
                    if (node) {
                        if (!item.translatedText || item.translatedText.trim() === "") {
                            // SKIP: Never replace with empty or blank text to avoid layout collapse or disappearing elements
                            return;
                        }
                        if (item.type === "text") {
                            var orig = node.originalText || node.nodeValue || "";
                            var leading = orig.match(/^\s*/)[0];
                            var trailing = orig.match(/\s*$/)[0];
                            node.nodeValue = leading + item.translatedText.trim() + trailing;
                            node.swiftLastTranslated = node.nodeValue;
                            replacedCount++;
                        } else if (item.type === "placeholder") {
                            node.setAttribute('placeholder', item.translatedText.trim());
                            replacedCount++;
                        } else if (item.type === "value") {
                            node.value = item.translatedText.trim();
                            replacedCount++;
                        }
                    }
                });
                return "replaced_" + replacedCount;
            })()
        """.trimIndent()
    }

    /**
     * Executes the replacement in the WebView.
     */
    fun replaceContent(webView: WebView, translationsJson: String, callback: (String?) -> Unit = {}) {
        webView.post {
            webView.evaluateJavascript(getReplacementJs(translationsJson)) { res ->
                callback(res)
            }
        }
    }
}
