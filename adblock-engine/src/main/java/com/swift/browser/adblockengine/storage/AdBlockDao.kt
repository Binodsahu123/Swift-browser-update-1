package com.swift.browser.adblockengine.storage

import android.content.ContentValues
import android.content.Context

/**
 * Access layer querying the sqlite database.
 */
class AdBlockDao(context: Context) {
    private val dbHelper = AdBlockDatabase(context)

    fun addException(domain: String, type: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AdBlockDatabase.COLUMN_DOMAIN, domain.lowercase())
            put(AdBlockDatabase.COLUMN_BLOCK_TYPE, type)
        }
        db.insertWithOnConflict(
            AdBlockDatabase.TABLE_EXCEPTIONS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeException(domain: String) {
        val db = dbHelper.writableDatabase
        db.delete(
            AdBlockDatabase.TABLE_EXCEPTIONS,
            "${AdBlockDatabase.COLUMN_DOMAIN} = ?",
            arrayOf(domain.lowercase())
        )
    }

    fun getExceptions(type: String): List<String> {
        val list = ArrayList<String>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AdBlockDatabase.TABLE_EXCEPTIONS,
            arrayOf(AdBlockDatabase.COLUMN_DOMAIN),
            "${AdBlockDatabase.COLUMN_BLOCK_TYPE} = ?",
            arrayOf(type),
            null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    fun recordSiteBlocked(domain: String) {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "INSERT OR REPLACE INTO ${AdBlockDatabase.TABLE_STATS} " +
                    "(${AdBlockDatabase.COLUMN_DOMAIN}, ${AdBlockDatabase.COLUMN_BLOCKED_COUNT}) " +
                    "VALUES (?, COALESCE((SELECT ${AdBlockDatabase.COLUMN_BLOCKED_COUNT} FROM ${AdBlockDatabase.TABLE_STATS} WHERE ${AdBlockDatabase.COLUMN_DOMAIN} = ?), 0) + 1)",
            arrayOf(domain.lowercase(), domain.lowercase())
        )
    }

    fun getSiteBlockedCount(domain: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AdBlockDatabase.TABLE_STATS,
            arrayOf(AdBlockDatabase.COLUMN_BLOCKED_COUNT),
            "${AdBlockDatabase.COLUMN_DOMAIN} = ?",
            arrayOf(domain.lowercase()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }
}
