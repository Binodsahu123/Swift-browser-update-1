package com.swift.browser.permissionengine

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PermissionEntity::class, ExtensionPermissionEntity::class], version = 2, exportSchema = false)
abstract class PermissionDatabase : RoomDatabase() {
    abstract fun permissionDao(): PermissionDao
    abstract fun extensionPermissionDao(): ExtensionPermissionDao

    companion object {
        @Volatile
        private var INSTANCE: PermissionDatabase? = null

        fun getDatabase(context: Context): PermissionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PermissionDatabase::class.java,
                    "permission_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
