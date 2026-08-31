package com.swift.browser.browserengine

import com.swift.browser.bookmarkengine.Bookmark
import com.swift.browser.bookmarkengine.api.BookmarkEngineApi
import com.swift.browser.downloadengine.DownloadConfig
import com.swift.browser.downloadengine.DownloadEngine
import com.swift.browser.downloadengine.DownloadItem
import com.swift.browser.historyengine.HistoryEngine
import com.swift.browser.historyengine.HistoryItem
import com.swift.browser.searchengine.SearchEngine
import com.swift.browser.searchengine.SearchEngineProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BrowserEngineIntegrationTest {

    @Test
    fun testHistoryEngineContract() = runBlocking {
        val historyStorage = mutableListOf<HistoryItem>()
        val historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())

        val mockHistoryEngine = object : HistoryEngine {
            override fun getHistoryFlow() = historyFlow.asStateFlow()
            override fun getRecentHistory(limit: Int) = historyFlow.asStateFlow()
            override suspend fun addHistoryItem(url: String, title: String, browsingContext: com.swift.browser.historyengine.BrowsingContext) {
                if (browsingContext.isPrivate) return
                val item = HistoryItem(id = historyStorage.size + 1, url = url, title = title, timestamp = System.currentTimeMillis())
                historyStorage.add(item)
                historyFlow.value = historyStorage.toList()
            }
            override suspend fun clearAllHistory() {
                historyStorage.clear()
                historyFlow.value = emptyList()
            }
            override suspend fun deleteHistoryItem(id: Int) {
                historyStorage.removeAll { it.id == id }
                historyFlow.value = historyStorage.toList()
            }
            override suspend fun deleteHistorySince(timestamp: Long) {}
            override suspend fun deleteHistoryRange(startTime: Long, endTime: Long) {}
            override suspend fun queryHistory(query: String) = historyStorage.filter { it.url.contains(query) || it.title.contains(query) }
            override suspend fun queryHistorySemantic(query: String) = queryHistory(query)
        }

        mockHistoryEngine.addHistoryItem("https://example.com", "Example Domain", com.swift.browser.historyengine.BrowsingContext())
        mockHistoryEngine.addHistoryItem("https://kotlinlang.org", "Kotlin", com.swift.browser.historyengine.BrowsingContext())

        assertEquals(2, mockHistoryEngine.getHistoryFlow().value.size)
        assertEquals("Example Domain", mockHistoryEngine.getHistoryFlow().value.first().title)

        mockHistoryEngine.deleteHistoryItem(1)
        assertEquals(1, mockHistoryEngine.getHistoryFlow().value.size)
        assertEquals("Kotlin", mockHistoryEngine.getHistoryFlow().value.first().title)

        mockHistoryEngine.clearAllHistory()
        assertTrue(mockHistoryEngine.getHistoryFlow().value.isEmpty())
    }

    @Test
    fun testDownloadEngineContractAndRename() = runBlocking {
        val downloadMap = mutableMapOf<Long, DownloadItem>()
        val downloadFlow = MutableStateFlow<List<DownloadItem>>(emptyList())

        val mockDownloadEngine = object : DownloadEngine {
            override fun getDownloadsFlow() = downloadFlow.asStateFlow()
            override fun getDownloadsByCategory(category: String) = downloadFlow.asStateFlow()
            override suspend fun startDownload(url: String, fileName: String, mimeType: String, threads: Int): Long {
                val id = (downloadMap.size + 1).toLong()
                val item = DownloadItem(
                    id = id,
                    title = fileName,
                    url = url,
                    mimeType = mimeType,
                    status = "RUNNING"
                )
                downloadMap[id] = item
                downloadFlow.value = downloadMap.values.toList()
                return id
            }
            override suspend fun pauseDownload(id: Long) {}
            override suspend fun resumeDownload(id: Long) {}
            override suspend fun cancelDownload(id: Long) {}
            override suspend fun deleteDownload(id: Long) {
                downloadMap.remove(id)
                downloadFlow.value = downloadMap.values.toList()
            }
            override suspend fun renameDownload(id: Long, newName: String) {
                downloadMap[id]?.let {
                    downloadMap[id] = it.copy(title = newName)
                    downloadFlow.value = downloadMap.values.toList()
                }
            }
            override fun setConfig(config: DownloadConfig) {}
            override fun getConfig() = DownloadConfig()
            override suspend fun insertOrUpdateDownload(item: DownloadItem) {}
        }

        val dlId = mockDownloadEngine.startDownload("https://example.com/file.zip", "old_name.zip", "application/zip", 4)
        assertEquals(1, mockDownloadEngine.getDownloadsFlow().value.size)
        assertEquals("old_name.zip", mockDownloadEngine.getDownloadsFlow().value.first().title)

        mockDownloadEngine.renameDownload(dlId, "new_renamed.zip")
        assertEquals("new_renamed.zip", mockDownloadEngine.getDownloadsFlow().value.first().title)

        mockDownloadEngine.deleteDownload(dlId)
        assertTrue(mockDownloadEngine.getDownloadsFlow().value.isEmpty())
    }

    @Test
    fun testBookmarkEngineContract() = runBlocking {
        val bookmarkList = mutableListOf<Bookmark>()
        val bookmarkFlow = MutableStateFlow<List<Bookmark>>(emptyList())

        val mockBookmarkEngine = object : BookmarkEngineApi {
            override val bookmarks = bookmarkFlow.asStateFlow()
            override fun init(context: android.content.Context) {}
            override fun addBookmark(url: String, title: String) {
                val b = Bookmark(id = (bookmarkList.size + 1), url = url, title = title)
                bookmarkList.add(b)
                bookmarkFlow.value = bookmarkList.toList()
            }
            override fun deleteBookmark(bookmark: Bookmark) {
                bookmarkList.removeAll { it.id == bookmark.id }
                bookmarkFlow.value = bookmarkList.toList()
            }
            override fun deleteBookmarkByUrl(url: String) {
                bookmarkList.removeAll { it.url == url }
                bookmarkFlow.value = bookmarkList.toList()
            }
            override fun deleteAllBookmarks() {
                bookmarkList.clear()
                bookmarkFlow.value = emptyList()
            }
            override suspend fun isBookmarked(url: String): Boolean {
                return bookmarkList.any { it.url == url }
            }
            override suspend fun toggleBookmark(url: String, title: String): Boolean {
                val existing = bookmarkList.find { it.url == url }
                return if (existing != null) {
                    deleteBookmark(existing)
                    false
                } else {
                    addBookmark(url, title)
                    true
                }
            }
            override suspend fun searchBookmarks(query: String, limit: Int): List<Bookmark> {
                return bookmarkList.filter { it.title.contains(query) || it.url.contains(query) }.take(limit)
            }
        }

        val added = mockBookmarkEngine.toggleBookmark("https://android.com", "Android")
        assertTrue(added)
        assertEquals(1, mockBookmarkEngine.bookmarks.value.size)

        val removed = mockBookmarkEngine.toggleBookmark("https://android.com", "Android")
        assertFalse(removed)
        assertTrue(mockBookmarkEngine.bookmarks.value.isEmpty())
    }

    @Test
    fun testSearchEngineUrlProcessing() {
        val searchEngine: SearchEngine = SearchEngineProvider.getEngine()
        val standardUrl = searchEngine.processInput("https://developer.android.com")
        assertEquals("https://developer.android.com", standardUrl)

        val searchQuery = searchEngine.processInput("kotlin coroutines best practices", "Google")
        assertTrue(searchQuery.startsWith("https://www.google.com/search?q="))
        assertTrue(searchQuery.contains("kotlin+coroutines"))
    }
}
