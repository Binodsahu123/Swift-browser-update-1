package com.swift.browser.notificationengine

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = NotificationDatabase.getDatabase(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val TAG = "NotificationWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic notification fetcher work")
        
        // 1. Ensure channels are loaded
        NotificationChannelManager.createNotificationChannels(appContext)

        // 2. Fetch all registered website subscriptions
        val subs = db.subscriptionDao().getAllSubscriptions()
        val permissionStore = WebsitePermissionStore(appContext)
        val allowedSubs = subs.filter { sub ->
            permissionStore.isAllowed(sub.websiteUrl)
        }

        if (allowedSubs.isEmpty()) {
            Log.d(TAG, "No active subscriptions found to update")
            return Result.success()
        }

        for (sub in allowedSubs) {
            val rssUrl = sub.customRssUrl ?: NotificationRegistry.resolveRssUrl(sub.websiteUrl)
            try {
                Log.d(TAG, "Fetching feed for website ${sub.websiteName} from $rssUrl")
                val entries = fetchRssFeed(rssUrl)
                if (entries.isNotEmpty()) {
                    // Check if the latest entry is newer than what we recorded, or if we have never saved updates
                    val latestEntry = entries.first()
                    val hasNewUpdate = sub.lastNotificationTitle != latestEntry.title && 
                                       sub.lastNotificationTime < latestEntry.timestamp

                    if (hasNewUpdate || sub.lastNotificationTime == 0L) {
                        Log.d(TAG, "New article found on ${sub.websiteName}: ${latestEntry.title}")
                        
                        // Show native notification if allowed
                        if (!sub.isMuted) {
                            showAndroidNotification(sub, latestEntry)
                        }

                        // Store history item
                        val historyItem = NotificationHistoryItem(
                            websiteUrl = sub.websiteUrl,
                            websiteName = sub.websiteName,
                            title = latestEntry.title,
                            body = latestEntry.description,
                            clickUrl = latestEntry.link,
                            timestamp = latestEntry.timestamp
                        )
                        db.historyDao().insertHistoryItem(historyItem)

                        // Update subscription meta
                        val updatedSub = sub.copy(
                            lastNotificationTitle = latestEntry.title,
                            lastNotificationTime = if (latestEntry.timestamp > 0L) latestEntry.timestamp else System.currentTimeMillis(),
                            notificationCount = sub.notificationCount + 1
                        )
                        db.subscriptionDao().insertSubscription(updatedSub)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update website notification for ${sub.websiteName}: ${e.message}")
            }
        }

        return Result.success()
    }

    private fun fetchRssFeed(url: String): List<RssEntry> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SwiftBrowser)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Server returned code ${response.code}")
            val bodyString = response.body?.string() ?: return emptyList()
            return parseXmlFeed(bodyString)
        }
    }

    private fun parseXmlFeed(xml: String): List<RssEntry> {
        val entries = mutableListOf<RssEntry>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""
        var isInsideItem = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        isInsideItem = true
                        title = ""
                        link = ""
                        description = ""
                        pubDate = ""
                    } else if (isInsideItem) {
                        when (tagName) {
                            "title" -> title = parser.nextText().trim()
                            "link" -> {
                                // Handles both standard <link>URL</link> and Atom-style <link href="URL"/>
                                val href = parser.getAttributeValue(null, "href")
                                link = if (!href.isNullOrBlank()) href.trim() else parser.nextText().trim()
                            }
                            "description", "summary" -> {
                                var text = parser.nextText().trim()
                                // Strip HTML tags for clean body notification displays
                                text = text.replace(Regex("<[^>]*>"), "")
                                if (text.length > 150) {
                                    text = text.substring(0, 147) + "..."
                                }
                                description = text
                            }
                            "pubDate", "pubdate", "updated", "published" -> {
                                pubDate = parser.nextText().trim()
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        if (title.isNotBlank()) {
                            val parsedTimestamp = parseDateToEpoch(pubDate)
                            entries.add(
                                RssEntry(
                                    title = title,
                                    link = link,
                                    description = if (description.isBlank()) "Read the latest news post" else description,
                                    timestamp = if (parsedTimestamp > 0) parsedTimestamp else System.currentTimeMillis()
                                )
                            )
                        }
                        isInsideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    private fun parseDateToEpoch(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val patterns = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(dateStr)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Keep checking alternative pattern
            }
        }
        return 0L
    }

    private fun showAndroidNotification(sub: NotificationSubscription, entry: RssEntry) {
        val channelId = NotificationChannelManager.getChannelId(
            priority = sub.priority,
            soundEnabled = sub.soundEnabled,
            vibrationEnabled = sub.vibrationEnabled,
            isMuted = sub.isMuted
        )

        // Target Action Intent: Launches browser deep link directly
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(entry.link)
            setPackage(appContext.packageName) // Ensure it target our app only
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notification_click_url", entry.link)
            putExtra("NOTIFICATION_URL", entry.link)
        }

        // PendingIntent wrapped safely
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            entry.link.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("[${sub.websiteName}] ${entry.title}")
            .setContentText(entry.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.description))
            .setPriority(when(sub.priority) {
                2 -> NotificationCompat.PRIORITY_HIGH
                0 -> NotificationCompat.PRIORITY_LOW
                else -> NotificationCompat.PRIORITY_DEFAULT
            })
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(entry.link.hashCode(), builder.build())
    }

    private data class RssEntry(
        val title: String,
        val link: String,
        val description: String,
        val timestamp: Long
    )
}
