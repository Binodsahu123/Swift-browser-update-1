package com.swift.browser.translateengine

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log

class MutationObserverManager(
    private val webView: WebView,
    private val onNewNodesDetected: (String) -> Unit
) {
    
    init {
        webView.post {
            webView.addJavascriptInterface(this, "SwiftTranslateInterface")
        }
    }

    /**
     * JS interface callback for dynamic nodes.
     */
    @JavascriptInterface
    fun onDynamicElementsDetected(jsonPayload: String) {
        Log.d("MutationObserverManager", "Received dynamic elements: $jsonPayload")
        onNewNodesDetected(jsonPayload)
    }

    /**
     * Activates the MutationObserver in the current web page.
     */
    fun startObserving(isDesktopMode: Boolean) {
        webView.post {
            val script = """
                (function() {
                    if (window.swiftObserverActive) return "already_active";
                    window.swiftObserverActive = true;
                    
                    var isDesktopMode = $isDesktopMode;
                    function shouldIncludeEl(el) {
                        var temp = el;
                        while (temp && temp !== document.body) {
                            var s = window.getComputedStyle(temp);
                            if (s.display === 'none' || s.visibility === 'hidden') {
                                var cls = temp.className;
                                if (typeof cls === 'string') {
                                    cls = cls.toLowerCase();
                                    if (isDesktopMode) {
                                        if (cls.indexOf('mobile') >= 0 || cls.indexOf('hidden-lg') >= 0 || cls.indexOf('hidden-xl') >= 0 || cls.indexOf('visible-xs') >= 0 || cls.indexOf('visible-sm') >= 0) {
                                            return false;
                                        }
                                    } else {
                                        if (cls.indexOf('desktop') >= 0 || cls.indexOf('hidden-xs') >= 0 || cls.indexOf('hidden-sm') >= 0 || cls.indexOf('visible-lg') >= 0 || cls.indexOf('visible-xl') >= 0) {
                                            return false;
                                        }
                                    }
                                }
                            }
                            temp = temp.parentElement;
                        }
                        return true;
                    }
                    
                    var observer = new MutationObserver(function(mutations) {
                        if (window.swiftTranslating) return;
                        
                        var addedTexts = [];
                        var idx = Object.keys(window.swiftTextNodes || {}).length;
                        window.swiftTextNodes = window.swiftTextNodes || {};
                        
                        mutations.forEach(function(mutation) {
                            if (mutation.type === 'characterData') {
                                var node = mutation.target;
                                var val = node.nodeValue ? node.nodeValue.trim() : "";
                                if (val.length > 0 && !val.match(/^(https?:\/\/|www\.)/i)) {
                                    if (node.nodeValue !== node.swiftLastTranslated) {
                                        var parent = node.parentElement;
                                        if (parent) {
                                            var tag = parent.tagName.toLowerCase();
                                            if (tag === 'script' || tag === 'style' || tag === 'iframe' || tag === 'code' || tag === 'noscript' || tag === 'pre' || tag === 'svg' || tag === 'canvas') {
                                                return;
                                            }
                                            if (!shouldIncludeEl(parent)) {
                                                return;
                                            }
                                        }
                                        if (!node.swiftTrId) {
                                            node.swiftTrId = "dyn_text_" + idx++;
                                            window.swiftTextNodes[node.swiftTrId] = node;
                                        }
                                        node.originalText = node.nodeValue;
                                        addedTexts.push({
                                            id: node.swiftTrId,
                                            type: "text",
                                            text: node.nodeValue
                                        });
                                    }
                                }
                                return;
                            }

                            mutation.addedNodes.forEach(function(node) {
                                var parent = node.parentElement;
                                if (parent) {
                                    var tag = parent.tagName.toLowerCase();
                                    if (tag === 'script' || tag === 'style' || tag === 'iframe' || tag === 'code' || tag === 'noscript' || tag === 'pre' || tag === 'svg' || tag === 'canvas') {
                                        return;
                                    }
                                    if (!shouldIncludeEl(parent)) {
                                        return;
                                    }
                                }
                                
                                if (node.nodeType === Node.TEXT_NODE) {
                                    var text = node.nodeValue ? node.nodeValue.trim() : "";
                                    if (text.length > 0 && !node.swiftTrId && !text.match(/^(https?:\/\/|www\.)/i)) {
                                        node.swiftTrId = "dyn_text_" + idx++;
                                        window.swiftTextNodes[node.swiftTrId] = node;
                                        node.originalText = node.nodeValue;
                                        addedTexts.push({
                                            id: node.swiftTrId,
                                            type: "text",
                                            text: node.nodeValue
                                        });
                                    }
                                } else if (node.nodeType === Node.ELEMENT_NODE) {
                                    if (!shouldIncludeEl(node)) return;
                                    
                                    var walker = document.createTreeWalker(
                                        node,
                                        NodeFilter.SHOW_TEXT,
                                        {
                                            acceptNode: function(n) {
                                                var p = n.parentElement;
                                                if (!p) return NodeFilter.FILTER_REJECT;
                                                var t = p.tagName.toLowerCase();
                                                if (t === 'script' || t === 'style' || t === 'iframe' || t === 'code' || t === 'noscript' || t === 'pre' || t === 'svg' || t === 'canvas') {
                                                    return NodeFilter.FILTER_REJECT;
                                                }
                                                if (!shouldIncludeEl(p)) {
                                                    return NodeFilter.FILTER_REJECT;
                                                }
                                                var txt = n.nodeValue ? n.nodeValue.trim() : "";
                                                if (txt.length === 0) return NodeFilter.FILTER_REJECT;
                                                if (txt.match(/^(https?:\/\/|www\.)/i)) return NodeFilter.FILTER_REJECT;
                                                return NodeFilter.FILTER_ACCEPT;
                                            }
                                        }
                                    );
                                    
                                    var childTextNode;
                                    while (childTextNode = walker.nextNode()) {
                                        if (!childTextNode.swiftTrId) {
                                            childTextNode.swiftTrId = "dyn_text_" + idx++;
                                            window.swiftTextNodes[childTextNode.swiftTrId] = childTextNode;
                                            childTextNode.originalText = childTextNode.nodeValue;
                                            addedTexts.push({
                                                id: childTextNode.swiftTrId,
                                                type: "text",
                                                text: childTextNode.nodeValue
                                            });
                                        }
                                    }

                                    // Dynamic Placeholders
                                    var placeholders = node.querySelectorAll ? node.querySelectorAll('input[placeholder], textarea[placeholder]') : [];
                                    placeholders.forEach(function(el) {
                                        if (!shouldIncludeEl(el)) return;
                                        if (!el.swiftTrId) {
                                            el.swiftTrId = "dyn_place_" + idx++;
                                            window.swiftTextNodes[el.swiftTrId] = el;
                                            el.originalPlaceholder = el.getAttribute('placeholder');
                                        }
                                        addedTexts.push({
                                            id: el.swiftTrId,
                                            type: "placeholder",
                                            text: el.getAttribute('placeholder')
                                        });
                                    });

                                    // Dynamic Buttons
                                    var buttons = node.querySelectorAll ? node.querySelectorAll('input[type="button"], input[type="submit"], input[type="reset"]') : [];
                                    buttons.forEach(function(el) {
                                        if (!shouldIncludeEl(el)) return;
                                        var val = el.value ? el.value.trim() : "";
                                        if (val.length === 0) return;
                                        if (!el.swiftTrId) {
                                            el.swiftTrId = "dyn_buttonval_" + idx++;
                                            window.swiftTextNodes[el.swiftTrId] = el;
                                            el.originalValue = el.value;
                                        }
                                        addedTexts.push({
                                            id: el.swiftTrId,
                                            type: "value",
                                            text: el.value
                                        });
                                    });
                                }
                            });
                        });
                        
                        if (addedTexts.length > 0) {
                            if (window.SwiftTranslateInterface) {
                                window.SwiftTranslateInterface.onDynamicElementsDetected(JSON.stringify(addedTexts));
                            }
                        }
                    });
                    
                    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
                    return "observer_activated";
                })()
            """.trimIndent()
            
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Pause observer state during background replaces.
     */
    fun pauseObserving() {
        webView.post {
            webView.evaluateJavascript("window.swiftTranslating = true;", null)
        }
    }

    /**
     * Resume observer state after background replaces are done.
     */
    fun resumeObserving() {
        webView.post {
            webView.evaluateJavascript("window.swiftTranslating = false;", null)
        }
    }
}
