package com.swift.browser.extensionengine

class DomBridge {
    /**
     * Compiles DOM utility scripts. This empowers injected scripts to fully access,
     * modify, and subscribe to mutations on document, window, body, forms, inputs, and buttons.
     */
    fun compileDomUtilities(): String {
        return """
            window.SwiftDOM = window.SwiftDOM || {
                query: function(selector) {
                    return document.querySelector(selector);
                },
                queryAll: function(selector) {
                    return Array.from(document.querySelectorAll(selector));
                },
                clickElement: function(selector) {
                    const el = document.querySelector(selector);
                    if (el) { el.click(); return true; }
                    return false;
                },
                setInputValue: function(selector, value) {
                    const el = document.querySelector(selector);
                    if (el) {
                        el.value = value;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                    }
                    return false;
                },
                onMutation: function(selector, callback) {
                    const observer = new MutationObserver((mutations) => {
                        const targets = document.querySelectorAll(selector);
                        if (targets.length > 0) {
                            callback(Array.from(targets));
                        }
                    });
                    observer.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                    return observer;
                }
            };
        """.trimIndent()
    }
}
