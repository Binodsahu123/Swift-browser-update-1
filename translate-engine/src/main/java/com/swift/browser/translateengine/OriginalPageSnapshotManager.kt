package com.swift.browser.translateengine

import android.webkit.WebView
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class PageNodeSnapshot(
    val nodeId: String,
    val xpath: String,
    val originalText: String,
    val type: String
)

object OriginalPageSnapshotManager {
    private val tabSnapshots = ConcurrentHashMap<String, Map<String, PageNodeSnapshot>>()

    fun hasSnapshot(tabId: String): Boolean {
        return tabSnapshots.containsKey(tabId)
    }

    fun clearSnapshot(tabId: String) {
        tabSnapshots.remove(tabId)
    }

    /**
     * Snapshots the original text and structure of the document to support high-fidelity restore without reloading.
     */
    fun captureSnapshot(webView: WebView, tabId: String, callback: (Int) -> Unit = {}) {
        val js = """
            (function() {
                function getXPath(node) {
                    if (node.nodeType === Node.TEXT_NODE) {
                        return getXPath(node.parentElement) + "/text()[" + (Array.from(node.parentElement.childNodes).filter(n => n.nodeType === Node.TEXT_NODE).indexOf(node) + 1) + "]";
                    }
                    if (node.id) {
                        return '//*[@id="' + node.id + '"]';
                    }
                    var parts = [];
                    while (node && node.nodeType === Node.ELEMENT_NODE) {
                        var sibCount = 0;
                        var sibIndex = 0;
                        for (var sib = node.previousSibling; sib; sib = sib.previousSibling) {
                            if (sib.nodeType === Node.ELEMENT_NODE && sib.nodeName === node.nodeName) {
                                sibCount++;
                            }
                        }
                        for (var sib = node.nextSibling; sib; sib = sib.nextSibling) {
                            if (sib.nodeType === Node.ELEMENT_NODE && sib.nodeName === node.nodeName) {
                                sibCount++;
                            }
                        }
                        var name = node.nodeName.toLowerCase();
                        if (sibCount > 0) {
                            sibIndex = 1;
                            for (var sib = node.previousSibling; sib; sib = sib.previousSibling) {
                                if (sib.nodeType === Node.ELEMENT_NODE && sib.nodeName === node.nodeName) {
                                    sibIndex++;
                                }
                            }
                            parts.unshift(name + '[' + sibIndex + ']');
                        } else {
                            parts.unshift(name);
                        }
                        node = node.parentNode;
                    }
                    return parts.length ? '/' + parts.join('/') : null;
                }

                if (!window.swiftTextNodes) return JSON.stringify([]);
                window.swiftOriginalSnapshot = window.swiftOriginalSnapshot || {};
                
                var snapshotList = [];
                for (var key in window.swiftTextNodes) {
                    var node = window.swiftTextNodes[key];
                    if (node) {
                        var xpath = getXPath(node) || "";
                        if (node.nodeType === Node.TEXT_NODE) {
                            if (node.originalText !== undefined) {
                                window.swiftOriginalSnapshot[key] = node.originalText;
                                snapshotList.push({
                                    id: key,
                                    xpath: xpath,
                                    text: node.originalText,
                                    type: "text"
                                });
                            }
                        } else if (node.tagName) {
                            if (node.originalPlaceholder !== undefined) {
                                window.swiftOriginalSnapshot[key + "_place"] = node.originalPlaceholder;
                                snapshotList.push({
                                    id: key,
                                    xpath: xpath,
                                    text: node.originalPlaceholder,
                                    type: "placeholder"
                                });
                            }
                            if (node.originalValue !== undefined) {
                                window.swiftOriginalSnapshot[key + "_val"] = node.originalValue;
                                snapshotList.push({
                                    id: key,
                                    xpath: xpath,
                                    text: node.originalValue,
                                    type: "value"
                                });
                            }
                        }
                    }
                }
                return JSON.stringify(snapshotList);
            })()
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js) { res ->
                if (res != null && res != "null" && res != "[]") {
                    try {
                        var jsonStr = res
                        if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                            jsonStr = jsonStr.substring(1, jsonStr.length - 1)
                            jsonStr = jsonStr.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
                        }
                        val array = JSONArray(jsonStr)
                        val map = ConcurrentHashMap<String, PageNodeSnapshot>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val id = obj.getString("id")
                            val xpath = obj.getString("xpath")
                            val text = obj.getString("text")
                            val type = obj.getString("type")
                            map[id] = PageNodeSnapshot(id, xpath, text, type)
                        }
                        tabSnapshots[tabId] = map
                        callback(map.size)
                    } catch (e: Exception) {
                        Log.e("OriginalPageSnapshot", "Error parsing capture snapshot", e)
                        callback(0)
                    }
                } else {
                    callback(0)
                }
            }
        }
    }

    /**
     * High-fidelity, instant restore of the original DOM contents without reloading.
     */
    fun restoreSnapshot(webView: WebView, tabId: String, callback: (Boolean) -> Unit = {}) {
        val js = """
            (function() {
                if (!window.swiftTextNodes || !window.swiftOriginalSnapshot) return false;
                var count = 0;
                for (var key in window.swiftTextNodes) {
                    var node = window.swiftTextNodes[key];
                    if (node) {
                        if (node.nodeType === Node.TEXT_NODE) {
                            var orig = window.swiftOriginalSnapshot[key];
                            if (orig !== undefined) {
                                node.nodeValue = orig;
                                count++;
                            }
                        } else if (node.tagName) {
                            var origPlace = window.swiftOriginalSnapshot[key + "_place"];
                            if (origPlace !== undefined) {
                                node.setAttribute('placeholder', origPlace);
                                count++;
                            }
                            var origVal = window.swiftOriginalSnapshot[key + "_val"];
                            if (origVal !== undefined) {
                                node.value = origVal;
                                count++;
                            }
                        }
                    }
                }
                return count > 0;
            })()
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js) { res ->
                callback(res == "true")
            }
        }
    }
}
