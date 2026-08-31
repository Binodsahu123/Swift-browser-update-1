package com.swift.browser.extensionengine

class PostMessageBridge {
    /**
     * Compiles script for managing MessageChannels and safe cross-origin PostMessage operations.
     */
    fun compilePostMessageScript(): String {
        return """
            window.SwiftPostMessage = window.SwiftPostMessage || {
                send: function(payload, targetOrigin) {
                    window.postMessage({
                        source: "swift-internal-bridge",
                        payload: payload
                    }, targetOrigin || "*");
                },
                listen: function(onMessageCallback) {
                    const listener = function(event) {
                        if (event.data && event.data.source === "swift-internal-bridge") {
                            onMessageCallback(event.data.payload, event.origin, event.source);
                        }
                    };
                    window.addEventListener("message", listener);
                    return function() {
                        window.removeEventListener("message", listener);
                    };
                }
            };
        """.trimIndent()
    }
}
