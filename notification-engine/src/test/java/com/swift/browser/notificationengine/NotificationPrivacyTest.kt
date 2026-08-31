package com.swift.browser.notificationengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swift.browser.notificationengine.data.NotificationRepository
import com.swift.browser.permissionengine.PermissionEngineProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationPrivacyTest {

    private lateinit var context: Context
    private lateinit var repository: NotificationRepository
    private lateinit var historyManager: NotificationHistoryManager
    private lateinit var permissionStore: WebsitePermissionStore

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        repository = NotificationRepository(context)
        historyManager = NotificationHistoryManager(context)
        permissionStore = WebsitePermissionStore(context)
        repository.clearHistory()
        historyManager.clearPrivateHistory()
    }

    @After
    fun tearDown() = runTest {
        repository.clearHistory()
        historyManager.clearPrivateHistory()
    }

    @Test
    fun testNormalNotificationPersistedInDatabase() = runTest {
        val normalUrl = "https://news.ycombinator.com"
        val normalTitle = "Top Hacker News Story"
        val normalBody = "Major tech breakthrough announced today"

        historyManager.addHistoryItem(
            websiteUrl = normalUrl,
            websiteName = "Hacker News",
            title = normalTitle,
            body = normalBody,
            clickUrl = normalUrl,
            browsingContext = NotificationBrowsingContext.NORMAL
        )

        // Must be in persistent database
        val dbHistory = repository.getAllHistory()
        assertEquals(1, dbHistory.size)
        assertEquals(normalUrl, dbHistory.first().websiteUrl)
        assertEquals(normalTitle, dbHistory.first().title)

        // Clear private history -> normal history remains unchanged
        historyManager.clearPrivateHistory("any_session")
        val dbHistoryAfter = repository.getAllHistory()
        assertEquals("Normal history in database must remain unchanged after private session cleanup", 1, dbHistoryAfter.size)
    }

    @Test
    fun testPrivateNotificationNotPersistedInDatabase() = runTest {
        val privateUrl = "https://medical-records.secure/results?patient=123"
        val privateTitle = "Confidential Lab Results"
        val privateBody = "Your private test report is available"

        historyManager.addHistoryItem(
            websiteUrl = privateUrl,
            websiteName = "Medical Portal",
            title = privateTitle,
            body = privateBody,
            clickUrl = privateUrl,
            browsingContext = NotificationBrowsingContext(isPrivate = true, privateSessionId = "incognito_123")
        )

        // 1. Database MUST NOT contain private URL, title, or body
        val dbHistory = repository.getAllHistory()
        assertTrue("Private notifications must NEVER be written to persistent database", dbHistory.isEmpty())

        // 2. Runtime in-memory history holds the item for the active session
        val runtimeHistory = historyManager.getPrivateHistoryList()
        assertEquals(1, runtimeHistory.size)
        assertEquals(privateTitle, runtimeHistory.first().title)
        assertEquals(privateUrl, runtimeHistory.first().clickUrl)
    }

    @Test
    fun testPrivateHistoryCleanupLeavesNormalHistoryIntact() = runTest {
        // Normal item
        historyManager.addHistoryItem(
            websiteUrl = "https://public-blog.com",
            websiteName = "Public Blog",
            title = "Public Post",
            body = "Hello World",
            clickUrl = "https://public-blog.com/1",
            browsingContext = NotificationBrowsingContext.NORMAL
        )

        // Private item
        historyManager.addHistoryItem(
            websiteUrl = "https://secret-forum.com",
            websiteName = "Secret Forum",
            title = "Secret Message",
            body = "Classified",
            clickUrl = "https://secret-forum.com/post",
            browsingContext = NotificationBrowsingContext(isPrivate = true)
        )

        assertEquals(1, repository.getAllHistory().size)
        assertEquals(1, historyManager.getPrivateHistoryList().size)

        // Close private session -> clear private history only
        historyManager.clearPrivateHistory()

        // Verify private runtime history is cleaned up
        assertTrue("Private history list should be empty after private session closes", historyManager.getPrivateHistoryList().isEmpty())

        // Verify normal persistent history is completely intact
        val remainingDbHistory = repository.getAllHistory()
        assertEquals(1, remainingDbHistory.size)
        assertEquals("Public Post", remainingDbHistory.first().title)
    }

    @Test
    fun testPermissionStillOwnedByPermissionEngine() = runTest {
        val testUrl = "https://example.org/feed"
        val permissionEngine = PermissionEngineProvider.get(context)

        // 1. Set permission directly on PermissionEngine
        permissionEngine.setPermissionState(testUrl, "NOTIFICATIONS", "ALLOW_ALWAYS")

        // WebsitePermissionStore must reflect PermissionEngine state
        val isAllowed = permissionStore.isAllowed(testUrl)
        assertTrue("WebsitePermissionStore should query PermissionEngine and return true when allowed", isAllowed)

        // 2. Change permission on PermissionEngine to BLOCK
        permissionEngine.setPermissionState(testUrl, "NOTIFICATIONS", "BLOCK")
        val isBlocked = permissionStore.isAllowed(testUrl)
        assertFalse("WebsitePermissionStore should query PermissionEngine and return false when blocked", isBlocked)
    }
}
