package com.swift.browser.historyengine

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@Database(entities = [HistoryItem::class], version = 1, exportSchema = false)
abstract class TestHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

@RunWith(RobolectricTestRunner::class)
class HistoryEnginePrivacyTest {

    private lateinit var db: TestHistoryDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var historyEngine: HistoryEngine

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, TestHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = db.historyDao()
        historyEngine = HistoryRepository(historyDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPrivateWriteIgnored() = runBlocking {
        // Attempt to write a private history item
        historyEngine.addHistoryItem(
            url = "https://secret-bank.com/account",
            title = "Secret Bank Account",
            browsingContext = BrowsingContext.PRIVATE
        )

        // Also test with explicit sessionId
        historyEngine.addHistoryItem(
            url = "https://private-search.com?q=confidential",
            title = "Confidential Search",
            browsingContext = BrowsingContext.private("session_abc")
        )

        // Also test with boolean helper
        historyEngine.addHistoryItem(
            url = "https://hidden-portal.com",
            title = "Hidden Portal",
            isPrivate = true
        )

        val historyList = historyEngine.getHistoryFlow().first()
        assertTrue("Private visits must NEVER reach Room history table", historyList.isEmpty())

        val recent = historyEngine.getRecentHistory(10).first()
        assertTrue(recent.isEmpty())
    }

    @Test
    fun testNormalWriteSaved() = runBlocking {
        // Normal write without explicit context (default to NORMAL)
        historyEngine.addHistoryItem(
            url = "https://kotlinlang.org",
            title = "Kotlin Programming Language"
        )

        // Normal write with explicit context
        historyEngine.addHistoryItem(
            url = "https://developer.android.com",
            title = "Android Developers",
            browsingContext = BrowsingContext.NORMAL
        )

        val historyList = historyEngine.getHistoryFlow().first()
        assertEquals(2, historyList.size)
        assertTrue(historyList.any { it.url == "https://kotlinlang.org" })
        assertTrue(historyList.any { it.url == "https://developer.android.com" })

        // Visit existing normal URL again -> increments visitCount and updates timestamp
        val beforeVisit = historyList.first { it.url == "https://kotlinlang.org" }
        historyEngine.addHistoryItem("https://kotlinlang.org", "Kotlin Programming Language")
        val afterVisit = historyEngine.getHistoryFlow().first().first { it.url == "https://kotlinlang.org" }
        assertEquals(2, afterVisit.visitCount)
        assertTrue(afterVisit.timestamp >= beforeVisit.timestamp)
    }

    @Test
    fun testPrivateSearchAbsent() = runBlocking {
        // Add normal items
        historyEngine.addHistoryItem("https://developer.android.com/reference", "Android API Reference", BrowsingContext.NORMAL)
        historyEngine.addHistoryItem("https://github.com/kotlin", "Kotlin on GitHub", BrowsingContext.NORMAL)

        // Add private items
        historyEngine.addHistoryItem("https://topsecret.com/crypto", "Top Secret Crypto", BrowsingContext.PRIVATE)
        historyEngine.addHistoryItem("https://private.medical.org", "Medical Records", BrowsingContext.PRIVATE)

        // Direct search
        val secretResults = historyEngine.queryHistory("secret")
        assertTrue("Direct query must not return private visits", secretResults.isEmpty())

        val cryptoResults = historyEngine.queryHistory("crypto")
        assertTrue(cryptoResults.isEmpty())

        // Semantic search
        val medicalResults = historyEngine.queryHistorySemantic("medical")
        assertTrue(medicalResults.isEmpty())

        // Normal search still functions properly
        val androidResults = historyEngine.queryHistory("android")
        assertEquals(1, androidResults.size)
        assertEquals("https://developer.android.com/reference", androidResults[0].url)

        val codeResults = historyEngine.queryHistorySemantic("code")
        assertTrue(codeResults.any { it.url.contains("github") })
    }

    @Test
    fun testNormalHistorySurvivesPrivateSessionAndPrivateCloseDoesNotClearNormal() = runBlocking {
        // Populate normal history
        historyEngine.addHistoryItem("https://wikipedia.org", "Wikipedia", BrowsingContext.NORMAL)
        historyEngine.addHistoryItem("https://docs.oracle.com", "Oracle Docs", BrowsingContext.NORMAL)

        assertEquals(2, historyEngine.getHistoryFlow().first().size)

        // Simulate active private session with private visits
        val privateContext = BrowsingContext.private("session_123")
        historyEngine.addHistoryItem("https://private-forum.org", "Private Forum", privateContext)
        historyEngine.addHistoryItem("https://incognito-chat.com", "Incognito Chat", privateContext)

        // Verify normal history remains exactly 2 items
        val historyDuringSession = historyEngine.getHistoryFlow().first()
        assertEquals(2, historyDuringSession.size)
        assertTrue(historyDuringSession.none { it.url.contains("private") || it.url.contains("incognito") })

        // Simulate private session close (do NOT call clearAllHistory on normal history!)
        // Normal history must survive intact
        val historyAfterSession = historyEngine.getHistoryFlow().first()
        assertEquals(2, historyAfterSession.size)
        assertEquals("Wikipedia", historyAfterSession.find { it.url == "https://wikipedia.org" }?.title)
        assertEquals("Oracle Docs", historyAfterSession.find { it.url == "https://docs.oracle.com" }?.title)
    }

    @Test
    fun testHistorySuggestionsExcludePrivatePages() = runBlocking {
        val normalItem = HistoryItem(1, "https://github.com", "GitHub", System.currentTimeMillis(), 5)
        val history = listOf(normalItem)

        val suggestions = HistorySearch.rankSuggestions(history, "git")
        assertEquals(1, suggestions.size)
        assertEquals("GitHub", suggestions[0].title)

        // Search for a keyword never in normal history
        val privateSuggestions = HistorySearch.rankSuggestions(history, "secret")
        assertTrue(privateSuggestions.isEmpty())
    }
}
