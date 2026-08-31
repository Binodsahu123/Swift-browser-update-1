package com.swift.browser.passwordengine.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import com.swift.browser.databasecore.DatabaseCore
import com.swift.browser.passwordengine.model.PasswordEntry

@Database(entities = [PasswordEntry::class], version = 1, exportSchema = false)
abstract class PasswordDatabase : RoomDatabase() {

    abstract fun passwordDao(): PasswordDao

    companion object {
        @Volatile
        private var INSTANCE: PasswordDatabase? = null

        fun getDatabase(context: Context): PasswordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabaseCore.buildDatabase(
                    context.applicationContext,
                    PasswordDatabase::class.java,
                    "swift_passwords.db"
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
