package com.swift.browser.aiengine

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Entity(tableName = "web_articles")
data class WebArticleEntity(
    @PrimaryKey val url: String,
    val title: String,
    val rawText: String,
    val extractedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompt_cache")
data class PromptCacheEntity(
    @PrimaryKey val key: String,
    val promptValue: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_responses")
data class AiResponseEntity(
    @PrimaryKey val cacheKey: String, // e.g. url_provider_length
    val responseText: String,
    val provider: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface AICacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: WebArticleEntity)

    @Query("SELECT * FROM web_articles WHERE url = :url LIMIT 1")
    suspend fun getArticleByUrl(url: String): WebArticleEntity?

    @Query("DELETE FROM web_articles WHERE url = :url")
    suspend fun deleteArticleByUrl(url: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptCacheEntity)

    @Query("SELECT * FROM prompt_cache WHERE `key` = :key LIMIT 1")
    suspend fun getPromptByKey(key: String): PromptCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(response: AiResponseEntity)

    @Query("SELECT * FROM ai_responses WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getResponseByKey(cacheKey: String): AiResponseEntity?

    @Query("DELETE FROM web_articles")
    suspend fun clearArticles()

    @Query("DELETE FROM prompt_cache")
    suspend fun clearPrompts()

    @Query("DELETE FROM ai_responses")
    suspend fun clearResponses()
}

@Database(
    entities = [WebArticleEntity::class, PromptCacheEntity::class, AiResponseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AICacheDatabase : RoomDatabase() {
    abstract fun aiCacheDao(): AICacheDao

    companion object {
        @Volatile
        private var INSTANCE: AICacheDatabase? = null

        fun getDatabase(context: Context): AICacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AICacheDatabase::class.java,
                    "swift_ai_cache_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object AICacheEngine {
    private const val TAG = "AICacheEngine"

    // Diagnostics / Performance Metrics
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val totalRequests = AtomicLong(0)

    // Memory Cache
    private val articleMemoryCache = ConcurrentHashMap<String, WebArticleEntity>()
    private val promptMemoryCache = ConcurrentHashMap<String, PromptCacheEntity>()
    private val responseMemoryCache = ConcurrentHashMap<String, AiResponseEntity>()

    private var database: AICacheDatabase? = null

    fun initialize(context: Context) {
        if (database == null) {
            database = AICacheDatabase.getDatabase(context)
        }
    }

    private fun getDao(): AICacheDao? {
        return database?.aiCacheDao()
    }

    // --- Article Cache ---
    suspend fun cacheArticle(url: String, title: String, rawText: String) {
        val entity = WebArticleEntity(url = cleanUrl(url), title = title, rawText = rawText)
        articleMemoryCache[entity.url] = entity
        try {
            getDao()?.insertArticle(entity)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cache article to database: ${e.localizedMessage}")
        }
    }

    suspend fun getCachedArticle(url: String): WebArticleEntity? {
        totalRequests.incrementAndGet()
        val clean = cleanUrl(url)
        val mem = articleMemoryCache[clean]
        if (mem != null) {
            hits.incrementAndGet()
            return mem
        }

        try {
            val dbItem = getDao()?.getArticleByUrl(clean)
            if (dbItem != null) {
                articleMemoryCache[clean] = dbItem
                hits.incrementAndGet()
                return dbItem
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed reading article from database: ${e.localizedMessage}")
        }

        misses.incrementAndGet()
        return null
    }

    // --- Prompt Cache ---
    suspend fun cachePrompt(key: String, promptValue: String) {
        val entity = PromptCacheEntity(key = key, promptValue = promptValue)
        promptMemoryCache[key] = entity
        try {
            getDao()?.insertPrompt(entity)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cache prompt to database: ${e.localizedMessage}")
        }
    }

    suspend fun getCachedPrompt(key: String): String? {
        totalRequests.incrementAndGet()
        val mem = promptMemoryCache[key]
        if (mem != null) {
            hits.incrementAndGet()
            return mem.promptValue
        }

        try {
            val dbItem = getDao()?.getPromptByKey(key)
            if (dbItem != null) {
                promptMemoryCache[key] = dbItem
                hits.incrementAndGet()
                return dbItem.promptValue
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed reading prompt from database: ${e.localizedMessage}")
        }

        misses.incrementAndGet()
        return null
    }

    // --- Response Cache ---
    suspend fun cacheResponse(cacheKey: String, responseText: String, provider: String) {
        val entity = AiResponseEntity(cacheKey = cacheKey, responseText = responseText, provider = provider)
        responseMemoryCache[cacheKey] = entity
        try {
            getDao()?.insertResponse(entity)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cache response to database: ${e.localizedMessage}")
        }
    }

    suspend fun getCachedResponse(cacheKey: String): AiResponseEntity? {
        totalRequests.incrementAndGet()
        val mem = responseMemoryCache[cacheKey]
        if (mem != null) {
            hits.incrementAndGet()
            return mem
        }

        try {
            val dbItem = getDao()?.getResponseByKey(cacheKey)
            if (dbItem != null) {
                responseMemoryCache[cacheKey] = dbItem
                hits.incrementAndGet()
                return dbItem
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed reading response from database: ${e.localizedMessage}")
        }

        misses.incrementAndGet()
        return null
    }

    // --- Diagnostics ---
    fun getPerformanceMetrics(): Map<String, Any> {
        val h = hits.get()
        val m = misses.get()
        val total = totalRequests.get()
        val ratio = if (total > 0) (h.toDouble() / total.toDouble()) * 100.0 else 0.0
        return mapOf(
            "hits" to h,
            "misses" to m,
            "totalRequests" to total,
            "hitRatioPercentage" to String.format("%.2f%%", ratio),
            "memoryArticlesCount" to articleMemoryCache.size,
            "memoryPromptsCount" to promptMemoryCache.size,
            "memoryResponsesCount" to responseMemoryCache.size
        )
    }

    suspend fun clearAll() {
        articleMemoryCache.clear()
        promptMemoryCache.clear()
        responseMemoryCache.clear()
        try {
            getDao()?.clearArticles()
            getDao()?.clearPrompts()
            getDao()?.clearResponses()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed clearing database: ${e.localizedMessage}")
        }
    }

    private fun cleanUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val builder = uri.buildUpon()
            builder.fragment(null).build().toString()
        } catch (e: Exception) {
            url
        }
    }
}
