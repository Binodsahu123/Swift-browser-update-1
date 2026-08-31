package com.swift.browser.adblockengine

import com.swift.browser.adblockengine.core.AdBlockWhitelistManager
import com.swift.browser.adblockengine.cosmetic.ElementHidingEngine
import org.junit.Assert.*
import org.junit.Test

class AdBlockEngineUnitTest {

    @Test
    fun testWhitelistDomainParsing() {
        assertFalse(AdBlockWhitelistManager.isWhitelisted("unknown-domain.com"))
        val domain = "  WWW.Example.com  "
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        assertEquals("example.com", host)
    }

    @Test
    fun testCosmeticFilterBypass() {
        val ytScript = ElementHidingEngine.getHidingJavascript("https://m.youtube.com/watch?v=123")
        assertEquals("", ytScript)

        val meetScript = ElementHidingEngine.getHidingJavascript("https://meet.google.com/abc-def-ghi")
        assertEquals("", meetScript)
    }
}
