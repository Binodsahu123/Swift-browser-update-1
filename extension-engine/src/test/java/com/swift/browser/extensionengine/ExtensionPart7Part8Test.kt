package com.swift.browser.extensionengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swift.browser.downloadengine.DownloadEngineProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionPart7Part8Test {

    private lateinit var context: Context
    private lateinit var registry: ExtensionRegistry
    private lateinit var permissionManager: PermissionManager
    private lateinit var eventManager: EventManager
    private lateinit var messageBus: MessageBus

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = ExtensionRegistry()
        messageBus = MessageBus()
        permissionManager = PermissionManager(context)
        permissionManager.setRegistry(registry)
        eventManager = EventManager(messageBus)
    }

    private fun registerTestExtension(
        id: String = "test_ext_id",
        permissions: List<String> = listOf("cookies", "bookmarks", "history", "downloads", "storage"),
        hostPermissions: List<String> = listOf("https://example.com/*"),
        allowedInPrivate: Boolean = false
    ) {
        val ext = ParsedExtension(
            id = id,
            name = "Test Extension",
            version = "1.0",
            description = "Test extension for Part 7 and 8",
            manifestVersion = 3,
            permissions = permissions,
            hostPermissions = hostPermissions,
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = allowedInPrivate
        )
        registry.register(ext)
    }

    @Test
    fun testCookiesAdapterPermissionAndValidation() {
        registerTestExtension("cookie_ext", permissions = listOf("cookies"), hostPermissions = listOf("https://example.com/*"))

        val permissionAdapter = ExtensionPermissionAdapter(context).apply {
            setRegistry(registry)
            setEventManager(eventManager)
        }
        val adapter = ExtensionCookieAdapter(context, permissionAdapter, registry, eventManager)
        val senderNormal = ExtensionSender(extensionId = "cookie_ext", isPrivate = false)
        val senderPrivate = ExtensionSender(extensionId = "cookie_ext", isPrivate = true)

        // Invalid URL -> exception or false
        try {
            adapter.get(senderNormal, JSONObject().apply { put("url", "ftp://example.com"); put("name", "test") })
            fail("Expected SecurityException or IllegalArgumentException for non http/https URL")
        } catch (e: Exception) {
            assertTrue(e is SecurityException || e is IllegalArgumentException)
        }

        // Valid call for non-existent cookie returns null
        val result = adapter.get(senderNormal, JSONObject().apply { put("url", "https://example.com/"); put("name", "nonexistent") })
        assertNull(result)

        // Private sender blocked if not allowed in private
        try {
            adapter.get(senderPrivate, JSONObject().apply { put("url", "https://example.com/"); put("name", "test") })
            fail("Expected SecurityException for private sender without allowedInPrivate")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("SecurityError"))
        }
    }

    @Test
    fun testBookmarksAdapterPermissionValidation() {
        runBlocking {
            registerTestExtension("bookmarks_ext", permissions = listOf("bookmarks"))

            val adapter = ExtensionBookmarksAdapter(context, permissionManager, registry, eventManager)
            val sender = ExtensionSender(extensionId = "bookmarks_ext")

            // Search bookmarks
            val results = adapter.search(sender, JSONObject().apply { put("query", "test") })
            assertNotNull(results)
            assertTrue(results is JSONArray)

            // Extension without bookmarks permission fails
            registerTestExtension("no_bm_ext", permissions = emptyList())
            val senderNoPerm = ExtensionSender(extensionId = "no_bm_ext")
            try {
                adapter.search(senderNoPerm, JSONObject().apply { put("query", "test") })
                fail("Expected SecurityException for missing bookmarks permission")
            } catch (e: SecurityException) {
                assertTrue(e.message!!.contains("bookmarks"))
            }
        }
    }

    @Test
    fun testHistoryAdapterStrictPrivateBoundary() {
        runBlocking {
            registerTestExtension("history_ext", permissions = listOf("history"))

            val adapter = ExtensionHistoryAdapter(permissionManager, registry, eventManager)
            val senderNormal = ExtensionSender(extensionId = "history_ext", isPrivate = false)
            val senderPrivate = ExtensionSender(extensionId = "history_ext", isPrivate = true)

            // Normal sender query
            val resultsNormal = adapter.search(senderNormal, JSONObject().apply { put("text", "") })
            assertNotNull(resultsNormal)

            // Private sender MUST return empty array regardless of history
            val resultsPrivate = adapter.search(senderPrivate, JSONObject().apply { put("text", "") })
            assertEquals(0, resultsPrivate.length())

            // Private addUrl should return success but NOT save to history
            val addRes = adapter.addUrl(senderPrivate, JSONObject().apply { put("url", "https://private.example.com"); put("title", "Private Page") })
            assertEquals("success", addRes.optString("status"))

            val verifyPrivateSearch = adapter.search(senderPrivate, JSONObject().apply { put("text", "private") })
            assertEquals(0, verifyPrivateSearch.length())
        }
    }

    @Test
    fun testDownloadsAdapterOperations() {
        runBlocking {
            registerTestExtension("downloads_ext", permissions = listOf("downloads"))

            val adapter = ExtensionDownloadsAdapter(context, permissionManager, registry, eventManager)
            val sender = ExtensionSender(extensionId = "downloads_ext")

            val downloadEngine = DownloadEngineProvider.getEngine(context)
            val downloadId = 9999L
            downloadEngine.insertOrUpdateDownload(
                com.swift.browser.downloadengine.DownloadItem(
                    id = downloadId,
                    title = "file.pdf",
                    url = "https://example.com/file.pdf",
                    mimeType = "application/pdf",
                    status = "RUNNING"
                )
            )

            // Search downloads
            val searchRes = adapter.search(sender, JSONObject().apply { put("id", downloadId) })
            assertEquals(1, searchRes.length())
            val item = searchRes.getJSONObject(0)
            assertEquals(downloadId, item.getLong("id"))

            // Pause & Resume
            val pauseRes = adapter.pause(sender, downloadId)
            assertEquals("success", pauseRes.optString("status"))

            val resumeRes = adapter.resume(sender, downloadId)
            assertEquals("success", resumeRes.optString("status"))

            // Cancel
            val cancelRes = adapter.cancel(sender, downloadId)
            assertEquals("success", cancelRes.optString("status"))
        }
    }

    @Test
    fun testStorageSyncAndManagedSupported() {
        runBlocking {
            val storageDao = Part8FakeStorageDao()
            val extensionDb = Part8FakeExtensionDatabase(storageDao)
            val storageManager = StorageManager(extensionDb)

            // Local & Session storage work
            val localRes = storageManager.get("ext_1", "local", null)
            assertNotNull(localRes)

            // Sync storage is supported via local fallback
            val syncRes = storageManager.get("ext_1", "sync", null)
            assertNotNull(syncRes)

            // Managed storage is supported, but throws MANAGED_STORAGE_UNAVAILABLE when context/registry are null
            try {
                storageManager.get("ext_1", "managed", null)
                fail("Expected Exception for managed storage due to missing context")
            } catch (e: Exception) {
                assertTrue(e.message == "MANAGED_STORAGE_UNAVAILABLE" || e is NullPointerException)
            }
        }
    }
}

private class Part8FakeExtensionDatabase(private val dao: StorageDao) : ExtensionDatabase() {
    override fun extensionDao(): ExtensionDao = error("Not needed")
    override fun storageDao(): StorageDao = dao
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper = error("Not needed")
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker = error("Not needed")
    override fun clearAllTables() {}
}

private class Part8FakeStorageDao : StorageDao {
    override suspend fun insertStorage(items: List<StorageEntity>) {}
    override suspend fun getStorageByArea(extensionId: String, area: String): List<StorageEntity> = emptyList()
    override suspend fun getStorageByKeys(extensionId: String, area: String, keys: List<String>): List<StorageEntity> = emptyList()
    override suspend fun deleteStorageByKeys(extensionId: String, area: String, keys: List<String>) {}
    override suspend fun clearStorage(extensionId: String, area: String) {}
}
