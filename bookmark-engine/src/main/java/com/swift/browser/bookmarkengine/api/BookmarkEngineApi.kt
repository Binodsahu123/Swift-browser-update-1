package com.swift.browser.bookmarkengine.api

import android.content.Context
import com.swift.browser.bookmarkengine.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface BookmarkEngineApi {
    val bookmarks: StateFlow<List<Bookmark>>
    fun init(context: Context)
    fun addBookmark(url: String, title: String)
    fun deleteBookmark(bookmark: Bookmark)
    fun deleteBookmarkByUrl(url: String)
    fun deleteAllBookmarks()
    suspend fun isBookmarked(url: String): Boolean
    suspend fun toggleBookmark(url: String, title: String): Boolean
    suspend fun searchBookmarks(query: String, limit: Int = 20): List<Bookmark>
}

class BookmarkEngineImpl(
    context: Context,
    private val scope: CoroutineScope
) : BookmarkEngineApi {
    private val database = BookmarkDatabase.getDatabase(context)
    private val repository = BookmarkRepository(database.bookmarkDao())

    override val bookmarks: StateFlow<List<Bookmark>> = repository.getBookmarksFlow()
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    override fun init(context: Context) {
        BookmarkDatabase.getDatabase(context)
    }

    override fun addBookmark(url: String, title: String) {
        scope.launch(Dispatchers.IO) {
            repository.addBookmark(url, title)
        }
    }

    override fun deleteBookmark(bookmark: Bookmark) {
        scope.launch(Dispatchers.IO) {
            repository.deleteBookmark(bookmark)
        }
    }

    override fun deleteBookmarkByUrl(url: String) {
        scope.launch(Dispatchers.IO) {
            repository.deleteBookmarkByUrl(url)
        }
    }

    override fun deleteAllBookmarks() {
        scope.launch(Dispatchers.IO) {
            repository.deleteAllBookmarks()
        }
    }

    override suspend fun isBookmarked(url: String): Boolean {
        return repository.isBookmarked(url)
    }

    override suspend fun toggleBookmark(url: String, title: String): Boolean {
        return repository.toggleBookmark(url, title)
    }

    override suspend fun searchBookmarks(query: String, limit: Int): List<Bookmark> {
        return repository.searchBookmarks(query, limit)
    }
}

object BookmarkEngineProvider {
    @Volatile
    private var instance: BookmarkEngineApi? = null

    fun getEngine(context: Context, scope: CoroutineScope): BookmarkEngineApi {
        return instance ?: synchronized(this) {
            instance ?: BookmarkEngineImpl(context.applicationContext, scope).also { instance = it }
        }
    }

    val api: BookmarkEngineApi
        get() = instance ?: synchronized(this) {
            instance ?: object : BookmarkEngineApi {
                private val _bm = kotlinx.coroutines.flow.MutableStateFlow<List<Bookmark>>(emptyList())
                override val bookmarks: StateFlow<List<Bookmark>> = _bm
                override fun init(context: Context) {
                    instance = BookmarkEngineImpl(context.applicationContext, kotlinx.coroutines.GlobalScope)
                }
                override fun addBookmark(url: String, title: String) {
                    instance?.addBookmark(url, title)
                }
                override fun deleteBookmark(bookmark: Bookmark) {
                    instance?.deleteBookmark(bookmark)
                }
                override fun deleteBookmarkByUrl(url: String) {
                    instance?.deleteBookmarkByUrl(url)
                }
                override fun deleteAllBookmarks() {
                    instance?.deleteAllBookmarks()
                }
                override suspend fun isBookmarked(url: String) = instance?.isBookmarked(url) ?: false
                override suspend fun toggleBookmark(url: String, title: String) = instance?.toggleBookmark(url, title) ?: false
                override suspend fun searchBookmarks(query: String, limit: Int) = instance?.searchBookmarks(query, limit) ?: emptyList()
            }
        }
}
