package com.swift.browser.translateengine

import android.content.Context
import androidx.room.*

@Entity(
    tableName = "translation_cache",
    primaryKeys = ["originalText", "targetLang"],
    indices = [
        Index(value = ["originalText", "targetLang"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class TranslationEntity(
    val originalText: String,
    val targetLang: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_cache WHERE originalText = :originalText AND targetLang = :targetLang LIMIT 1")
    suspend fun getTranslation(originalText: String, targetLang: String): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(translation: TranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(translations: List<TranslationEntity>)

    @Query("DELETE FROM translation_cache WHERE timestamp < :expiryTime")
    suspend fun pruneOldTranslations(expiryTime: Long)

    @Query("DELETE FROM translation_cache")
    suspend fun clearCache()

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int
}

@Database(entities = [TranslationEntity::class], version = 1, exportSchema = false)
abstract class TranslateDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
}
