package com.swift.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey





@Entity(tableName = "top_sites")
data class TopSite(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val isCustom: Boolean = false,
    val isHidden: Boolean = false,
    val visitCount: Int = 0
)

@Entity(tableName = "articles")
data class ArticleCacheEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val sourceUrl: String,
    val sourceName: String,
    val publishedAt: String,
    val category: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val downloadId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val status: String, // "PENDING", "DOWNLOADING", "COMPLETE", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tab_sessions")
data class TabSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val isSuspended: Boolean,
    val lastActiveTime: Long,
    val scrollX: Int,
    val scrollY: Int,
    val groupColor: String?,
    val groupName: String?,
    val isIncognito: Boolean = false,
    val parentTabId: String? = null
)


