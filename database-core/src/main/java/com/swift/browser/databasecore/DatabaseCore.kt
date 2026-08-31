package com.swift.browser.databasecore

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object DatabaseCore {
    fun <T : RoomDatabase> buildDatabase(
        context: Context,
        klass: Class<T>,
        dbName: String
    ): T {
        return Room.databaseBuilder(
            context.applicationContext,
            klass,
            dbName
        )
        .fallbackToDestructiveMigration(true)
        .build()
    }
}
