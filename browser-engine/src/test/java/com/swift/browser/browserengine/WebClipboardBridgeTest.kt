package com.swift.browser.browserengine

import org.junit.Assert.*
import org.junit.Test

class WebClipboardBridgeTest {

    @Test
    fun testPolyfillJsContent() {
        val polyfill = WebClipboardBridge.getPolyfillJs()
        assertTrue("Polyfill must reference AndroidClipboardBridge interface",
            polyfill.contains("AndroidClipboardBridge"))
        assertTrue("Polyfill must implement readText",
            polyfill.contains("readText:"))
        assertTrue("Polyfill must implement writeText",
            polyfill.contains("writeText:"))
        assertTrue("Polyfill must implement navigator.clipboard",
            polyfill.contains("navigator.clipboard"))
        assertTrue("Polyfill must handle read callback",
            polyfill.contains("__swift_clipboard_onReadResponse"))
        assertTrue("Polyfill must handle write callback",
            polyfill.contains("__swift_clipboard_onWriteResponse"))
    }

    @Test
    fun testInterfaceNameConstant() {
        assertEquals("AndroidClipboardBridge", WebClipboardBridge.INTERFACE_NAME)
    }
}
