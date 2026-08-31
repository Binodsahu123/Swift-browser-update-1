package com.swift.browser.tabengine

import com.swift.browser.tabengine.engine.TabEngine
import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.tabengine.repository.TabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PrivateTabEngineTest {

    private lateinit var tabEngine: TabEngine
    private lateinit var tabRepository: TabRepository

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        tabRepository = TabRepository(app)
        tabEngine = TabEngine(
            context = app,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun testCreatePrivateTabAndGroupCanonicalMethods() {
        val sessionId = "session_abc_123"
        val privateTab = tabEngine.createPrivateTab(
            sessionId = sessionId,
            url = "https://example.com/private",
            title = "Private 1"
        )

        assertTrue(privateTab.isPrivate)
        assertTrue(privateTab.isIncognito)
        assertEquals(sessionId, privateTab.privateSessionId)

        val activeGroup = tabEngine.getActiveGroup()
        assertNotNull(activeGroup)
        assertTrue(activeGroup!!.isPrivate)
        assertEquals(sessionId, activeGroup.privateSessionId)

        val privateTabs = tabEngine.getPrivateTabs(sessionId)
        assertEquals(1, privateTabs.size)
        assertEquals(privateTab.id, privateTabs[0].id)

        val normalTabs = tabEngine.getNormalTabs()
        assertEquals(0, normalTabs.size)
    }

    @Test
    fun testPrivateGroupIsolationFromNormalGroup() {
        // Create normal group and tab
        val normalGroup = tabEngine.createGroup("Work", isIncognito = false)
        val normalTab = tabEngine.createTab("https://work.com", "Work", isIncognito = false, groupId = normalGroup.id)
        assertFalse(normalTab.isPrivate)
        assertNull(normalTab.privateSessionId)

        // Create private tab without explicit groupId when normal group is active
        val privateTab = tabEngine.createTab("https://private.com", "Private", isIncognito = true, groupId = null)
        assertTrue(privateTab.isPrivate)
        assertNotNull(privateTab.privateSessionId)

        // Ensure private tab is NOT placed in the normal group
        val currentNormalGroup = tabEngine.groups.value.find { it.id == normalGroup.id }
        assertNotNull(currentNormalGroup)
        assertEquals(1, currentNormalGroup!!.tabs.size)
        assertEquals(normalTab.id, currentNormalGroup.tabs[0].id)

        // Ensure a separate private group holds the private tab
        val privateGroup = tabEngine.groups.value.find { it.id != normalGroup.id }
        assertNotNull(privateGroup)
        assertTrue(privateGroup!!.isPrivate)
        assertEquals(1, privateGroup.tabs.size)
        assertEquals(privateTab.id, privateGroup.tabs[0].id)
    }

    @Test
    fun testNormalAndPrivateMixingRejection() {
        val normalGroup = tabEngine.createGroup("Work", isIncognito = false)
        val normalTab = tabEngine.createTab("https://work.com", "Work", isIncognito = false, groupId = normalGroup.id)

        val privateGroup = tabEngine.createPrivateGroup("sess_1", "Private Group")
        val privateTab = tabEngine.createPrivateTab("sess_1", "https://secret.com", "Secret", groupId = privateGroup.id)

        // Attempt to move normal tab to private group -> MUST BE REJECTED
        tabEngine.moveTabToGroup(normalTab.id, privateGroup.id)
        val groupAfterNormalMove = tabEngine.groups.value.find { it.id == privateGroup.id }!!
        assertEquals(1, groupAfterNormalMove.tabs.size)
        assertEquals(privateTab.id, groupAfterNormalMove.tabs[0].id)

        // Attempt to move private tab to normal group -> MUST BE REJECTED
        tabEngine.moveTabToGroup(privateTab.id, normalGroup.id)
        val groupAfterPrivateMove = tabEngine.groups.value.find { it.id == normalGroup.id }!!
        assertEquals(1, groupAfterPrivateMove.tabs.size)
        assertEquals(normalTab.id, groupAfterPrivateMove.tabs[0].id)
    }

    @Test
    fun testPersistenceExcludesPrivateTabsAndGroups() {
        val normalGroup = tabEngine.createGroup("Normal Group", isIncognito = false)
        tabEngine.createTab("https://google.com", "Google", isIncognito = false, groupId = normalGroup.id)

        val privateGroup = tabEngine.createPrivateGroup("sess_private", "Private Group")
        tabEngine.createPrivateTab("sess_private", "https://duckduckgo.com", "DDG", groupId = privateGroup.id)

        // Save session
        tabEngine.saveSession()

        // Load directly via repository
        val loaded = tabRepository.loadGroups()
        assertEquals(1, loaded.size)
        assertEquals("Normal Group", loaded[0].name)
        assertFalse(loaded[0].isPrivate)
        assertFalse(loaded[0].isIncognito)
        assertEquals(1, loaded[0].tabs.size)
        assertEquals("https://google.com", loaded[0].tabs[0].url)
        assertFalse(loaded[0].tabs[0].isPrivate)
    }

    @Test
    fun testRestoreIgnoresLegacyPersistedPrivateTabs() {
        // Direct save with mock JSON containing a private tab / group
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("tab_engine_session", android.content.Context.MODE_PRIVATE)
        val legacyJson = """
            [
                {
                    "id": "g_norm",
                    "name": "Normal Group",
                    "color": 0,
                    "isIncognito": false,
                    "isPrivate": false,
                    "activeTabId": "t_1",
                    "tabs": [
                        {"id": "t_1", "url": "https://normal.com", "title": "Normal", "isIncognito": false, "isPrivate": false},
                        {"id": "t_incog", "url": "https://incog.com", "title": "Incog", "isIncognito": true, "isPrivate": true}
                    ]
                },
                {
                    "id": "g_priv",
                    "name": "Private Group",
                    "color": 0,
                    "isIncognito": true,
                    "isPrivate": true,
                    "activeTabId": "t_priv",
                    "tabs": [
                        {"id": "t_priv", "url": "https://secret.com", "title": "Secret", "isIncognito": true, "isPrivate": true}
                    ]
                }
            ]
        """.trimIndent()
        prefs.edit().putString("groups", legacyJson).commit()

        val loaded = tabRepository.loadGroups()
        assertEquals(1, loaded.size)
        assertEquals("Normal Group", loaded[0].name)
        assertEquals(1, loaded[0].tabs.size)
        assertEquals("https://normal.com", loaded[0].tabs[0].url)
    }

    @Test
    fun testClosingLastPrivateTabRemovesPrivateGroupAndPreservesNormalTabs() {
        val normalGroup = tabEngine.createGroup("Default", isIncognito = false)
        val normalTab = tabEngine.createTab("https://news.com", "News", isIncognito = false, groupId = normalGroup.id)

        val privateTab = tabEngine.createPrivateTab("sess_x", "https://anon.com", "Anon")
        val privateGroupId = privateTab.groupId!!

        assertEquals(2, tabEngine.groups.value.size)

        // Close the private tab
        tabEngine.closePrivateTab(privateTab.id)

        // Private group should be removed
        assertNull(tabEngine.groups.value.find { it.id == privateGroupId })
        assertEquals(1, tabEngine.groups.value.size)

        // Normal tab and group must remain untouched
        val remainingGroup = tabEngine.groups.value[0]
        assertEquals(normalGroup.id, remainingGroup.id)
        assertEquals(1, remainingGroup.tabs.size)
        assertEquals(normalTab.id, remainingGroup.tabs[0].id)
    }
}
