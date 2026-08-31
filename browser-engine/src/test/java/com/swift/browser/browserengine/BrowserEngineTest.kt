package com.swift.browser.browserengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BrowserEngineTest {
    @Test
    fun testNavigationEngineUrlCleaning() {
        val navEngine = NavigationEngine()
        val res = navEngine.cleanUrl("google.com")
        assertEquals("https://google.com", res)
    }

    @Test
    fun testTabMemoryManagerLru() {
        val memoryManager = TabMemoryManager(maxActiveTabs = 2)
        memoryManager.recordTabAccess("tab_a")
        memoryManager.recordTabAccess("tab_b")
        memoryManager.recordTabAccess("tab_c")
        
        val trimCandidates = memoryManager.getTabsToTrim(listOf("tab_a", "tab_b", "tab_c"))
        // Safe to assert index and size
        assertEquals(1, trimCandidates.size)
        assertEquals("tab_a", trimCandidates[0])
    }
}
