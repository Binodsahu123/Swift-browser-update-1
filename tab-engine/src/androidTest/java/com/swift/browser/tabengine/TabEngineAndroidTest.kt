package com.swift.browser.tabengine

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swift.browser.tabengine.engine.TabEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@RunWith(AndroidJUnit4::class)
class TabEngineAndroidTest {
    @Test
    fun testTabCreationAndSwitching() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val tabEngine = TabEngine(context, scope)
        tabEngine.initialize()

        // Test creating a tab
        val initialGroups = tabEngine.groups.value
        assertEquals(1, initialGroups.size)
        
        val newTab = tabEngine.createTab("https://www.example.com", "Example")
        assertNotNull(newTab)
        assertEquals("https://www.example.com", newTab.url)

        // Test active tab update
        assertEquals(newTab.id, tabEngine.activeTabId.value)
        val activeTab = tabEngine.getActiveTab()
        assertNotNull(activeTab)
        assertEquals(newTab.id, activeTab?.id)
        
        // Test tab switching
        val anotherTab = tabEngine.createTab("https://www.test.com", "Test")
        assertEquals(anotherTab.id, tabEngine.activeTabId.value)
        
        tabEngine.switchTab(newTab.id)
        assertEquals(newTab.id, tabEngine.activeTabId.value)
        
        // Test tab removal
        tabEngine.closeTab(newTab.id)
        val remainingTab = tabEngine.getActiveTab()
        // Behavior when closing active tab varies, but the tab should be gone
        val allTabs = tabEngine.groups.value.flatMap { it.tabs }
        assertTrue(allTabs.none { it.id == newTab.id })
    }
}
