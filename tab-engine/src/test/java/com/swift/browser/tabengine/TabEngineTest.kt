package com.swift.browser.tabengine

import com.swift.browser.tabengine.engine.TabEngine
import com.swift.browser.tabengine.model.TabModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TabEngineTest {

    private lateinit var tabEngine: TabEngine

    @Before
    fun setUp() {
        tabEngine = TabEngine(
            context = RuntimeEnvironment.getApplication(),
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun testTabCreation() {
        val tab = TabModel(id = "tab_1", url = "https://example.com", title = "Example")
        assertEquals("https://example.com", tab.url)
        assertEquals("Example", tab.title)
    }

    @Test
    fun testTabEngineAddAndSelectTab() {
        val group = tabEngine.createGroup("Main", false)
        assertNotNull(group)

        val tab1 = tabEngine.createTab("https://site1.com", "Site 1", false, group.id)
        assertNotNull(tab1)
        assertEquals("https://site1.com", tab1.url)

        val tab2 = tabEngine.createTab("https://site2.com", "Site 2", false, group.id)
        assertNotNull(tab2)
        assertEquals(tab2.id, tabEngine.activeTabId.value)

        // Switch to tab1
        tabEngine.switchTab(tab1.id)
        assertEquals(tab1.id, tabEngine.activeTabId.value)
    }

    @Test
    fun testTabCloseLifecycle() {
        val group = tabEngine.createGroup("Main", false)
        val tab1 = tabEngine.createTab("https://site1.com", "Site 1", false, group.id)
        val tab2 = tabEngine.createTab("https://site2.com", "Site 2", false, group.id)

        assertEquals(tab2.id, tabEngine.activeTabId.value)

        // Close tab2
        tabEngine.closeTab(tab2.id)

        // Fallback active tab
        assertEquals(tab1.id, tabEngine.activeTabId.value)

        // Close tab1
        tabEngine.closeTab(tab1.id)
        assertNull(tabEngine.activeTabId.value)
    }

    @Test
    fun testNonExistentTabOperations() {
        tabEngine.closeTab("non_existent_tab_id")
        assertNull(tabEngine.activeTabId.value)
    }
}



