package com.swift.browser.browserengine

import org.junit.Assert.*
import org.junit.Test

class BrowserNavigationApiTest {

    @Test
    fun testUrlNormalizationRules() {
        val raw1 = "http://EXAMPLE.COM/path/?query=1"
        val normalized1 = BrowserNavigationEngine.normalizeUrl(raw1)
        assertEquals("https://example.com/path?query=1", normalized1)

        val raw2 = "https://sub.domain.org/test/page/"
        val normalized2 = BrowserNavigationEngine.normalizeUrl(raw2)
        assertEquals("https://sub.domain.org/test/page", normalized2)
    }

    @Test
    fun testNavigationSourceCategorization() {
        val userReq = NavigationRequest(
            tabId = "tab_1",
            url = "https://example.com",
            source = NavigationSource.USER_INPUT,
            applyDesktopPolicy = true
        )
        assertTrue(userReq.applyDesktopPolicy)
        assertEquals(NavigationSource.USER_INPUT, userReq.source)

        val extensionReq = NavigationRequest(
            tabId = "tab_1",
            url = "https://example.com",
            source = NavigationSource.EXTENSION,
            applyDesktopPolicy = false
        )
        assertFalse(extensionReq.applyDesktopPolicy)
        assertEquals(NavigationSource.EXTENSION, extensionReq.source)
    }

    @Test
    fun testTabMemoryManagerEvictionOrder() {
        val memoryManager = TabMemoryManager(maxActiveTabs = 3)
        memoryManager.recordTabAccess("tab_1")
        memoryManager.recordTabAccess("tab_2")
        memoryManager.recordTabAccess("tab_3")
        memoryManager.recordTabAccess("tab_4")

        val toTrim = memoryManager.getTabsToTrim(listOf("tab_1", "tab_2", "tab_3", "tab_4"))
        assertEquals(1, toTrim.size)
        assertEquals("tab_1", toTrim.first())
    }
}
