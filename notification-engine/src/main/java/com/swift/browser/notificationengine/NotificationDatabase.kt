package com.swift.browser.notificationengine

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notification_subscriptions")
data class NotificationSubscription(
    @PrimaryKey val websiteUrl: String,
    val websiteName: String,
    val permission: String, // "ALLOW", "BLOCK", "ASK"
    val enabled: Boolean = true,
    val customRssUrl: String? = null,
    val lastNotificationTitle: String? = null,
    val lastNotificationTime: Long = 0L,
    val notificationCount: Int = 0,
    val isMuted: Boolean = false,
    val priority: Int = 1, // 0 = Low, 1 = Default, 2 = High
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val pauseUntil: Long = 0L // timestamp (epoch ms) to pause notifications until, 0 if not paused
)

@Entity(tableName = "notification_history")
data class NotificationHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val websiteUrl: String,
    val websiteName: String,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val clickUrl: String,
    val isRead: Boolean = false
)

@Dao
interface NotificationSubscriptionDao {
    @Query("SELECT * FROM notification_subscriptions")
    fun getAllSubscriptionsFlow(): Flow<List<NotificationSubscription>>

    @Query("SELECT * FROM notification_subscriptions")
    suspend fun getAllSubscriptions(): List<NotificationSubscription>

    @Query("SELECT * FROM notification_subscriptions WHERE websiteUrl = :websiteUrl LIMIT 1")
    suspend fun getSubscription(websiteUrl: String): NotificationSubscription?

    @Query("SELECT * FROM notification_subscriptions WHERE websiteUrl = :websiteUrl LIMIT 1")
    fun getSubscriptionFlow(websiteUrl: String): Flow<NotificationSubscription?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: NotificationSubscription)

    @Update
    suspend fun updateSubscription(subscription: NotificationSubscription)

    @Delete
    suspend fun deleteSubscription(subscription: NotificationSubscription)

    @Query("DELETE FROM notification_subscriptions WHERE websiteUrl = :websiteUrl")
    suspend fun deleteSubscriptionByUrl(websiteUrl: String)
}

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<NotificationHistoryItem>>

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<NotificationHistoryItem>

    @Query("SELECT * FROM notification_history WHERE websiteUrl = :websiteUrl ORDER BY timestamp DESC")
    fun getHistoryForWebsiteFlow(websiteUrl: String): Flow<List<NotificationHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: NotificationHistoryItem)

    @Query("UPDATE notification_history SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Int)

    @Query("UPDATE notification_history SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Int)

    @Query("DELETE FROM notification_history")
    suspend fun clearAllHistory()
}

@Database(entities = [NotificationSubscription::class, NotificationHistoryItem::class], version = 1, exportSchema = false)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): NotificationSubscriptionDao
    abstract fun historyDao(): NotificationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: NotificationDatabase? = null

        fun getDatabase(context: Context): NotificationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotificationDatabase::class.java,
                    "swift_notification_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
