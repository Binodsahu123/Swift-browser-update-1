package com.swift.browser.translateengine

import android.webkit.WebView

object DomExtractionEngine {
    
    /**
     * Builds and returns Javascript to extract translatable content.
     * Extracts text nodes (excluding script, style, code, iframe tags, and strings that look like URLs)
     * and input/textarea placeholders. Registers element references in `window.swiftTextNodes`.
     */
    fun getExtractionJs(isDesktopMode: Boolean): String {
        return """
            (function() {
                var isDesktopMode = $isDesktopMode;
                function shouldIncludeEl(el) {
                    var temp = el;
                    while (temp && temp !== document.body) {
                        var s = window.getComputedStyle(temp);
                        if (s && (s.display === 'none' || s.visibility === 'hidden')) {
                            return false;
                        }
                        temp = temp.parentElement;
                    }
                    return true;
                }

                var walker = document.createTreeWalker(
                    document.body,
                    NodeFilter.SHOW_TEXT,
                    {
                        acceptNode: function(node) {
                            var parent = node.parentElement;
                            if (!parent) return NodeFilter.FILTER_REJECT;
                            var tag = parent.tagName.toLowerCase();
                            if (tag === 'script' || tag === 'style' || tag === 'iframe' || tag === 'code' || tag === 'noscript' || tag === 'pre' || tag === 'svg' || tag === 'canvas') {
                                return NodeFilter.FILTER_REJECT;
                            }
                            if (!shouldIncludeEl(parent)) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            var text = node.nodeValue ? node.nodeValue.trim() : "";
                            if (text.length === 0) return NodeFilter.FILTER_REJECT;
                            if (text.match(/^(https?:\/\/|www\.)/i)) return NodeFilter.FILTER_REJECT;
                            return NodeFilter.FILTER_ACCEPT;
                        }
                    }
                );
                
                var items = [];
                var node;
                var idx = 0;
                window.swiftTextNodes = window.swiftTextNodes || {};
                window.swiftOriginalSnapshot = window.swiftOriginalSnapshot || {};
                
                while(node = walker.nextNode()) {
                    if (!node.swiftTrId) {
                        node.swiftTrId = "text_" + idx++;
                        window.swiftTextNodes[node.swiftTrId] = node;
                        node.originalText = node.nodeValue;
                        window.swiftOriginalSnapshot[node.swiftTrId] = node.nodeValue;
                    }
                    var textVal = node.nodeValue ? node.nodeValue.trim() : "";
                    if (textVal.length > 0) {
                        items.push({
                            id: node.swiftTrId,
                            type: "text",
                            text: textVal
                        });
                    }
                }
                
                var placeholders = document.querySelectorAll('input[placeholder], textarea[placeholder]');
                placeholders.forEach(function(el) {
                    if (!shouldIncludeEl(el)) return;
                    if (!el.swiftTrId) {
                        el.swiftTrId = "place_" + idx++;
                        window.swiftTextNodes[el.swiftTrId] = el;
                        el.originalPlaceholder = el.getAttribute('placeholder');
                        window.swiftOriginalSnapshot[el.swiftTrId + "_place"] = el.getAttribute('placeholder');
                    }
                    var placeVal = el.getAttribute('placeholder') ? el.getAttribute('placeholder').trim() : "";
                    if (placeVal.length > 0) {
                        items.push({
                            id: el.swiftTrId,
                            type: "placeholder",
                            text: placeVal
                        });
                    }
                });

                var buttons = document.querySelectorAll('input[type="button"], input[type="submit"], input[type="reset"]');
                buttons.forEach(function(el) {
                    if (!shouldIncludeEl(el)) return;
                    var val = el.value ? el.value.trim() : "";
                    if (val.length === 0) return;
                    if (!el.swiftTrId) {
                        el.swiftTrId = "buttonval_" + idx++;
                        window.swiftTextNodes[el.swiftTrId] = el;
                        el.originalValue = el.value;
                        window.swiftOriginalSnapshot[el.swiftTrId + "_val"] = el.value;
                    }
                    items.push({
                        id: el.swiftTrId,
                        type: "value",
                        text: val
                    });
                });
                
                return JSON.stringify(items);
            })()
        """.trimIndent()
    }

    /**
     * Executes extraction script on a given webview.
     */
    fun extractContent(webView: WebView, isDesktopMode: Boolean, callback: (String?) -> Unit) {
        webView.post {
            webView.evaluateJavascript(getExtractionJs(isDesktopMode)) { value ->
                if (value == null || value == "null") {
                    callback(null)
                } else {
                    // Stripping outer escape quotes if returned as a JSON string literal
                    var cleaned = value
                    if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                        cleaned = cleaned.substring(1, cleaned.length - 1)
                        // Decode simple unicode escape sequences and JSON characters
                        cleaned = cleaned.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
                    }
                    callback(cleaned)
                }
            }
        }
    }
}
