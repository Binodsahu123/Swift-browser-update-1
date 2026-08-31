package com.swift.browser.data

import com.swift.browser.historyengine.HistoryItem
import com.swift.browser.historyengine.HistoryDao


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryItem::class, TopSite::class, ArticleCacheEntity::class, DownloadItem::class, TabSessionEntity::class], version = 4, exportSchema = false)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun topSiteDao(): TopSiteDao
    abstract fun articleDao(): ArticleDao
    abstract fun downloadDao(): DownloadDao
    abstract fun tabSessionDao(): TabSessionDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "swift_browser_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
