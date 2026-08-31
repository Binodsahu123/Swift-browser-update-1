package com.swift.browser.newsengine

import android.content.Context
import androidx.room.*
import com.swift.browser.newsengine.api.NewsEngineApi
import com.swift.browser.newsengine.state.NewsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Entity(tableName = "engine_news")
data class NewsItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val sourceUrl: String,
    val sourceName: String,
    val publishedAt: String,
    val category: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface NewsDao {
    @Query("SELECT * FROM engine_news ORDER BY cachedAt DESC")
    fun getAllNewsFlow(): Flow<List<NewsItemEntity>>

    @Query("SELECT * FROM engine_news WHERE category = :category ORDER BY cachedAt DESC")
    fun getNewsByCategoryFlow(category: String): Flow<List<NewsItemEntity>>

    @Query("SELECT * FROM engine_news WHERE category = :category ORDER BY cachedAt DESC")
    suspend fun getNewsByCategory(category: String): List<NewsItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NewsItemEntity>)

    @Query("DELETE FROM engine_news WHERE cachedAt < :expiry")
    suspend fun deleteOldNews(expiry: Long)
}

@Database(entities = [NewsItemEntity::class], version = 1, exportSchema = false)
abstract class NewsLocalDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
}

interface NewsEngine {
    fun getNewsFlow(): Flow<List<NewsItemEntity>>
    suspend fun refreshNews(rssUrl: String, category: String)
    fun getFeedUrlForCategory(category: String): String
}

class NewsRepository(private val context: Context) : NewsEngine {
    private val okHttpClient = okhttp3.OkHttpClient()
    internal val db: NewsLocalDatabase by lazy {
        com.swift.browser.databasecore.DatabaseCore.buildDatabase(
            context,
            NewsLocalDatabase::class.java,
            "swift_news_engine_database"
        )
    }

    override fun getNewsFlow(): Flow<List<NewsItemEntity>> = db.newsDao().getAllNewsFlow()

    fun getNewsByCategoryFlow(category: String): Flow<List<NewsItemEntity>> =
        db.newsDao().getNewsByCategoryFlow(category)

    suspend fun getCachedNewsByCategory(category: String): List<NewsItemEntity> =
        db.newsDao().getNewsByCategory(category)

    override suspend fun refreshNews(rssUrl: String, category: String) {
        val fetched = RssFeedParser.fetchAndParseRss(okHttpClient, rssUrl, category)
        if (fetched.isNotEmpty()) {
            db.newsDao().insertAll(fetched)
        }
    }

    override fun getFeedUrlForCategory(category: String): String {
        return when (category) {
            "For You", "Top Stories" -> "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"
            "India" -> "https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms"
            "Tech", "Technology" -> "https://timesofindia.indiatimes.com/rssfeeds/66949542.cms"
            "Sports" -> "https://timesofindia.indiatimes.com/rssfeeds/4719148.cms"
            "Entertainment" -> "https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms"
            "Business" -> "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms"
            "Science" -> "https://timesofindia.indiatimes.com/rssfeeds/-2128672765.cms"
            "Health" -> "https://timesofindia.indiatimes.com/rssfeeds/3908999.cms"
            else -> "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"
        }
    }
}

class NewsEngineImpl(
    private val context: Context,
    private val scope: CoroutineScope
) : NewsEngineApi, NewsEngine {
    private val repository = NewsRepository(context)
    private val _uiState = MutableStateFlow(NewsUiState(feedCategory = "For You", isFeedLoading = false))
    override val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    init {
        selectCategory("For You")
    }

    override fun selectCategory(category: String) {
        currentJob?.cancel()
        _uiState.value = _uiState.value.copy(
            feedCategory = category,
            isFeedLoading = true,
            error = null
        )

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Load cached articles first for immediate response
                val cached = repository.getCachedNewsByCategory(category)
                if (cached.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        articles = cached,
                        isFeedLoading = false
                    )
                }

                // 2. Fetch fresh articles via RSS
                val rssUrl = getFeedUrlForCategory(category)
                repository.refreshNews(rssUrl, category)

                // 3. Update with latest database contents
                val latest = repository.getCachedNewsByCategory(category)
                _uiState.value = _uiState.value.copy(
                    articles = if (latest.isNotEmpty()) latest else cached,
                    isFeedLoading = false,
                    lastRefresh = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isFeedLoading = false,
                    error = e.localizedMessage
                )
            }
        }
    }

    override fun refresh() {
        selectCategory(_uiState.value.feedCategory)
    }

    override fun getNewsFlow(): Flow<List<NewsItemEntity>> = repository.getNewsFlow()

    override suspend fun refreshNews(rssUrl: String, category: String) =
        repository.refreshNews(rssUrl, category)

    override fun getFeedUrlForCategory(category: String): String =
        repository.getFeedUrlForCategory(category)
}

class NewsCache(private val context: Context) {
    private val repository = NewsRepository(context)

    suspend fun clearOldCache(expiryDurationMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val expiry = System.currentTimeMillis() - expiryDurationMs
        repository.db.newsDao().deleteOldNews(expiry)
    }
}
