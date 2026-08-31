package com.swift.browser.extensionengine

class PageBridge {
    /**
     * Compiles a javascript bridge executed in the page context to handle
     * event orchestration and cross-context message routing.
     */
    fun compileBridgeScript(): String {
        return """
            (function() {
                if (window.__swiftPageBridgeLoaded) return;
                window.__swiftPageBridgeLoaded = true;

                // Handle postMessage bridging
                window.addEventListener("message", function(event) {
                    if (event.data && event.data.source === "swift-page") {
                        // Forward message to content script side as CustomEvent
                        const customEvent = new CustomEvent("swift-to-contentscript", {
                            detail: event.data.payload
                        });
                        window.dispatchEvent(customEvent);
                    }
                });

                // Listen to Content Script events and forward to Page if needed
                window.addEventListener("swift-to-page", function(event) {
                    window.postMessage({
                        source: "swift-content-script",
                        payload: event.detail
                    }, "*");
                });
            })();
        """.trimIndent()
    }
}
