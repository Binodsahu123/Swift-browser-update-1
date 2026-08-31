package com.swift.browser.extensionengine

import android.content.Context
import com.swift.browser.bookmarkengine.Bookmark
import com.swift.browser.bookmarkengine.api.BookmarkEngineApi
import com.swift.browser.bookmarkengine.api.BookmarkEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject

/**
 * ExtensionBookmarksAdapter bridges chrome.bookmarks.* calls to Orion's bookmark-engine.
 */
class ExtensionBookmarksAdapter(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bookmarkApi: BookmarkEngineApi by lazy {
        BookmarkEngineProvider.getEngine(context, scope)
    }

    private fun verifyBookmarksPermission(sender: ExtensionSender) {
        val ext = registry.getExtension(sender.extensionId)
            ?: throw SecurityException("SecurityError: Extension ${sender.extensionId} not found.")
        if (!permissionManager.hasApiPermission(sender.extensionId, ext.permissions, "bookmarks")) {
            throw SecurityException("SecurityError: Extension does not have 'bookmarks' permission in manifest.")
        }
    }

    private fun formatBookmarkNode(bookmark: Bookmark, parentId: String = "1", index: Int = 0): JSONObject {
        return JSONObject().apply {
            put("id", bookmark.id.toString())
            put("parentId", parentId)
            put("index", index)
            put("title", bookmark.title)
            put("url", bookmark.url)
            put("dateAdded", bookmark.timestamp)
        }
    }

    private fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val parsed = android.net.Uri.parse(url)
            parsed.scheme != null && parsed.host != null
        } catch (e: Exception) {
            false
        }
    }

    fun get(sender: ExtensionSender, idOrIds: Any): JSONArray {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value
        val result = JSONArray()

        val targetIds = when (idOrIds) {
            is String -> listOf(idOrIds)
            is Number -> listOf(idOrIds.toString())
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until idOrIds.length()) {
                    list.add(idOrIds.get(i).toString())
                }
                list
            }
            else -> emptyList()
        }

        for (id in targetIds) {
            val idx = allBookmarks.indexOfFirst { it.id.toString() == id }
            if (idx != -1) {
                result.put(formatBookmarkNode(allBookmarks[idx], "1", idx))
            } else {
                throw IllegalArgumentException("BOOKMARK_NOT_FOUND")
            }
        }
        return result
    }

    fun getChildren(sender: ExtensionSender, id: String): JSONArray {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value
        val result = JSONArray()

        // Root folders (0, 1) contain all bookmarks
        if (id == "0" || id == "1" || id == "root") {
            allBookmarks.forEachIndexed { index, bm ->
                result.put(formatBookmarkNode(bm, "1", index))
            }
        } else {
            // Check if folder id exists, else throw BOOKMARK_NOT_FOUND
            val exists = allBookmarks.any { it.id.toString() == id }
            if (!exists) {
                throw IllegalArgumentException("BOOKMARK_NOT_FOUND")
            }
        }
        return result
    }

    fun getRecent(sender: ExtensionSender, numberOfItems: Int): JSONArray {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value.sortedByDescending { it.timestamp }
        val limit = numberOfItems.coerceAtLeast(1).coerceAtMost(allBookmarks.size)
        val result = JSONArray()

        for (i in 0 until limit) {
            result.put(formatBookmarkNode(allBookmarks[i], "1", i))
        }
        return result
    }

    fun getTree(sender: ExtensionSender): JSONArray {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value
        val root = JSONObject().apply {
            put("id", "0")
            put("title", "")
            val children = JSONArray()

            val barFolder = JSONObject().apply {
                put("id", "1")
                put("parentId", "0")
                put("title", "Bookmarks Bar")
                val barChildren = JSONArray()
                allBookmarks.forEachIndexed { index, bm ->
                    barChildren.put(formatBookmarkNode(bm, "1", index))
                }
                put("children", barChildren)
            }
            children.put(barFolder)
            put("children", children)
        }

        return JSONArray().put(root)
    }

    fun getSubTree(sender: ExtensionSender, id: String): JSONArray {
        verifyBookmarksPermission(sender)
        if (id != "0" && id != "1" && id != "root") {
            val allBookmarks = bookmarkApi.bookmarks.value
            val exists = allBookmarks.any { it.id.toString() == id }
            if (!exists) {
                throw IllegalArgumentException("BOOKMARK_NOT_FOUND")
            }
        }
        return getTree(sender)
    }

    suspend fun search(sender: ExtensionSender, queryInput: Any): JSONArray {
        verifyBookmarksPermission(sender)
        val queryStr = when (queryInput) {
            is String -> queryInput
            is JSONObject -> queryInput.optString("query", queryInput.optString("title", queryInput.optString("url", "")))
            else -> ""
        }

        val matches = if (queryStr.isBlank()) {
            bookmarkApi.bookmarks.value
        } else {
            bookmarkApi.searchBookmarks(queryStr, 100)
        }

        val result = JSONArray()
        matches.forEachIndexed { index, bm ->
            result.put(formatBookmarkNode(bm, "1", index))
        }
        return result
    }

    suspend fun create(sender: ExtensionSender, bookmarkData: JSONObject): JSONObject {
        verifyBookmarksPermission(sender)
        val title = bookmarkData.optString("title", "New Bookmark")
        val url = bookmarkData.optString("url", "")
        val parentId = bookmarkData.optString("parentId", "1")

        if (url.isNotBlank() && !isValidUrl(url)) {
            throw IllegalArgumentException("BOOKMARK_INVALID_URL")
        }
        if (parentId != "0" && parentId != "1" && parentId != "root") {
            throw IllegalArgumentException("BOOKMARK_PARENT_NOT_FOUND")
        }

        bookmarkApi.addBookmark(url, title)
        kotlinx.coroutines.delay(20)

        val newlyCreated = bookmarkApi.bookmarks.value.find { it.url == url }
        val idVal = newlyCreated?.id ?: (System.currentTimeMillis() % 100000).toInt()
        val newBm = Bookmark(id = idVal, url = url, title = title)
        val node = formatBookmarkNode(newBm, parentId, 0)

        // Event dispatch
        eventManager.triggerEvent("bookmarks.onCreated", JSONObject().apply {
            put("id", node.getString("id"))
            put("bookmark", node)
        })

        return node
    }

    suspend fun update(sender: ExtensionSender, id: String, changes: JSONObject): JSONObject {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value
        val existing = allBookmarks.find { it.id.toString() == id }
            ?: throw IllegalArgumentException("BOOKMARK_NOT_FOUND")

        val newTitle = if (changes.has("title")) changes.getString("title") else existing.title
        val newUrl = if (changes.has("url")) changes.getString("url") else existing.url

        if (newUrl.isNotBlank() && !isValidUrl(newUrl)) {
            throw IllegalArgumentException("BOOKMARK_INVALID_URL")
        }

        if (existing.url != newUrl) {
            bookmarkApi.deleteBookmarkByUrl(existing.url)
        }
        bookmarkApi.addBookmark(newUrl, newTitle)

        val updatedNode = formatBookmarkNode(existing.copy(title = newTitle, url = newUrl))

        eventManager.triggerEvent("bookmarks.onChanged", JSONObject().apply {
            put("id", id)
            put("changeInfo", JSONObject().apply {
                put("title", newTitle)
                put("url", newUrl)
            })
        })

        return updatedNode
    }

    fun move(sender: ExtensionSender, id: String, destination: JSONObject): JSONObject {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value
        val exists = allBookmarks.any { it.id.toString() == id }
        if (!exists) {
            throw IllegalArgumentException("BOOKMARK_NOT_FOUND")
        }
        val parentId = destination.optString("parentId", "1")
        if (parentId != "0" && parentId != "1" && parentId != "root") {
            throw IllegalArgumentException("BOOKMARK_PARENT_NOT_FOUND")
        }
        val index = destination.optInt("index", 0)

        val node = JSONObject().apply {
            put("id", id)
            put("parentId", parentId)
            put("index", index)
        }

        eventManager.triggerEvent("bookmarks.onMoved", JSONObject().apply {
            put("id", id)
            put("moveInfo", destination)
        })

        return node
    }

    suspend fun remove(sender: ExtensionSender, id: String): JSONObject {
        verifyBookmarksPermission(sender)
        val allBookmarks = bookmarkApi.bookmarks.value
        val existing = allBookmarks.find { it.id.toString() == id }
            ?: throw IllegalArgumentException("BOOKMARK_NOT_FOUND")
        
        bookmarkApi.deleteBookmarkByUrl(existing.url)

        eventManager.triggerEvent("bookmarks.onRemoved", JSONObject().apply {
            put("id", id)
            put("removeInfo", JSONObject().apply {
                put("parentId", "1")
                put("index", 0)
            })
        })

        return JSONObject().apply { put("id", id) }
    }

    suspend fun removeTree(sender: ExtensionSender, id: String): JSONObject {
        return remove(sender, id)
    }
}
