package com.swift.browser.adblockengine.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Native SQLite helper managing the internal database structure for rules and logs.
 */
class AdBlockDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "adblock_subsystem.db"
        const val DATABASE_VERSION = 1

        const val TABLE_EXCEPTIONS = "site_exceptions"
        const val COLUMN_ID = "id"
        const val COLUMN_DOMAIN = "domain"
        const val COLUMN_BLOCK_TYPE = "block_type" // whitelist or blacklist

        const val TABLE_STATS = "site_stats"
        const val COLUMN_BLOCKED_COUNT = "blocked_count"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_EXCEPTIONS (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DOMAIN TEXT UNIQUE, " +
                    "$COLUMN_BLOCK_TYPE TEXT)"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_STATS (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DOMAIN TEXT UNIQUE, " +
                    "$COLUMN_BLOCKED_COUNT INTEGER DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EXCEPTIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_STATS")
        onCreate(db)
    }
}
