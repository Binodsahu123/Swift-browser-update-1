package com.swift.browser.bookmarkengine

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import com.swift.browser.databasecore.DatabaseCore

@Database(entities = [Bookmark::class], version = 1, exportSchema = false)
abstract class BookmarkDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: BookmarkDatabase? = null

        fun getDatabase(context: Context): BookmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabaseCore.buildDatabase(
                    context.applicationContext,
                    BookmarkDatabase::class.java,
                    "swift_bookmarks.db"
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
