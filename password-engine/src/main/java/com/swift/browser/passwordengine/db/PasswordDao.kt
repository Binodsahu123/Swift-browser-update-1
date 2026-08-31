package com.swift.browser.passwordengine.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.swift.browser.passwordengine.model.PasswordEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Query("SELECT * FROM passwords ORDER BY siteTitle ASC")
    fun getAllPasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE isFavorite = 1 ORDER BY siteTitle ASC")
    fun getFavoritePasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE category = :category ORDER BY siteTitle ASC")
    fun getPasswordsByCategory(category: String): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE siteUrl LIKE '%' || :query || '%' OR siteTitle LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%'")
    fun searchPasswords(query: String): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE siteUrl LIKE '%' || :domain || '%' OR siteTitle LIKE '%' || :domain || '%'")
    suspend fun getPasswordsForDomain(domain: String): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getPasswordById(id: Long): PasswordEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(entry: PasswordEntry): Long

    @Update
    suspend fun updatePassword(entry: PasswordEntry)

    @Delete
    suspend fun deletePassword(entry: PasswordEntry)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: Long)

    @Query("SELECT COUNT(*) FROM passwords")
    fun getPasswordCount(): Flow<Int>
}
