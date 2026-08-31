package com.swift.browser.downloadengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "swift_downloads")
data class DownloadItem(
    @PrimaryKey val id: Long,
    val title: String,
    val url: String,
    val mimeType: String,
    val status: String, // "PENDING", "RUNNING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED", "SCHEDULED"
    val progress: Int = 0,
    val filePath: String = "",
    val totalSize: Long = 0L,
    val downloadedSize: Long = 0L,
    val speed: String = "0 KB/s",
    val timestamp: Long = System.currentTimeMillis(),
    val threads: Int = 4,
    val category: String = "Documents", // "Videos", "Audio", "Images", "Documents", "Archives"
    val scheduledTime: Long = 0L, // 0 means not scheduled, otherwise epoch millis
    val errorMessage: String = "", // details of the failure if any
    val queueOrder: Int = 0, // queuing position order number
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM swift_downloads ORDER BY timestamp DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM swift_downloads WHERE isPrivate = 0 ORDER BY timestamp DESC")
    fun getNonPrivateDownloadsFlow(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM swift_downloads WHERE status = 'SCHEDULED'")
    suspend fun getScheduledDownloads(): List<DownloadItem>

    @Query("SELECT * FROM swift_downloads WHERE status = 'RUNNING'")
    suspend fun getRunningDownloads(): List<DownloadItem>

    @Query("SELECT * FROM swift_downloads WHERE status = 'PENDING' ORDER BY queueOrder ASC, timestamp ASC")
    suspend fun getPendingDownloadsSorted(): List<DownloadItem>

    @Query("SELECT * FROM swift_downloads WHERE category = :category ORDER BY timestamp DESC")
    fun getDownloadsByCategoryFlow(category: String): Flow<List<DownloadItem>>

    @Query("SELECT * FROM swift_downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    @Query("SELECT * FROM swift_downloads WHERE isPrivate = 1 AND (:sessionId IS NULL OR privateSessionId = :sessionId)")
    suspend fun getPrivateDownloads(sessionId: String? = null): List<DownloadItem>

    @Query("DELETE FROM swift_downloads WHERE isPrivate = 1 AND (:sessionId IS NULL OR privateSessionId = :sessionId)")
    suspend fun deletePrivateDownloads(sessionId: String? = null)

    @Query("UPDATE swift_downloads SET url = '[PRIVATE_DOWNLOAD]', isPrivate = 0, privateSessionId = NULL WHERE isPrivate = 1 AND (:sessionId IS NULL OR privateSessionId = :sessionId)")
    suspend fun redactPrivateMetadata(sessionId: String? = null)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadItem)

    @Update
    suspend fun updateDownload(download: DownloadItem)

    @Query("UPDATE swift_downloads SET status = :status, progress = :progress, downloadedSize = :downloaded, speed = :speed WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Int, downloaded: Long, speed: String)

    @Query("DELETE FROM swift_downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM swift_downloads")
    suspend fun deleteAll()
}

@Database(entities = [DownloadItem::class], version = 4, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getDatabase(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "swift_download_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
