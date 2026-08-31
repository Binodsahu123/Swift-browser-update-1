package com.swift.browser.passwordengine.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val siteUrl: String,
    val siteTitle: String,
    val username: String,
    val encryptedPassword: String,
    val notes: String = "",
    val category: String = PasswordCategory.GENERAL.displayName,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val strengthRating: Int = 0
)
