package com.swift.browser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow





@Dao
interface TopSiteDao {
    @Query("SELECT * FROM top_sites")
    fun getAllTopSites(): Flow<List<TopSite>>

    @Query("SELECT * FROM top_sites WHERE url = :url LIMIT 1")
    suspend fun getTopSiteByUrl(url: String): TopSite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopSite(topSite: TopSite)

    @Update
    suspend fun updateTopSite(topSite: TopSite)

    @Delete
    suspend fun deleteTopSite(topSite: TopSite)
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE category = :category ORDER BY cachedAt DESC")
    fun getArticlesByCategory(category: String): Flow<List<ArticleCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleCacheEntity>)

    @Query("DELETE FROM articles WHERE category = :category")
    suspend fun deleteArticlesByCategory(category: String)

    @Query("DELETE FROM articles")
    suspend fun clearAllArticles()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadItem)

    @Query("UPDATE downloads SET status = :status WHERE downloadId = :id")
    suspend fun updateDownloadStatus(id: Long, status: String)

    @Query("UPDATE downloads SET fileName = :fileName WHERE downloadId = :id")
    suspend fun updateDownloadFileName(id: Long, fileName: String)

    @Query("DELETE FROM downloads WHERE downloadId = :id")
    suspend fun deleteDownload(id: Long)
}

@Dao
interface TabSessionDao {
    @Query("""
        SELECT * FROM tab_sessions 
        WHERE isIncognito = 0 
        ORDER BY lastActiveTime DESC
    """)
    fun getAllTabsFlow(): Flow<List<TabSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTab(tab: TabSessionEntity)

    @Delete
    suspend fun deleteTab(tab: TabSessionEntity)

    @Query("DELETE FROM tab_sessions")
    suspend fun deleteAllTabs()

    @Query("""
        UPDATE tab_sessions 
        SET scrollX=:x, scrollY=:y 
        WHERE id=:tabId
    """)
    suspend fun updateScroll(
        tabId: String, x: Int, y: Int
    )
}


