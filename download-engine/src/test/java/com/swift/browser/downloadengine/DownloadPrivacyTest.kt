package com.swift.browser.downloadengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DownloadPrivacyTest {

    private lateinit var context: Context
    private lateinit var repository: DownloadRepository
    private lateinit var downloadEngine: DownloadManagerImpl
    private val createdTestFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = DownloadRepository(context)
        downloadEngine = DownloadManagerImpl(context)
    }

    @After
    fun tearDown() = runTest {
        repository.clearAll()
        for (file in createdTestFiles) {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @Test
    fun testNormalDownloadUnchangedByPrivateSessionCleanup() = runTest {
        val normalId = 1001L
        val normalItem = DownloadItem(
            id = normalId,
            title = "normal_document.pdf",
            url = "https://example.com/files/normal_document.pdf",
            mimeType = "application/pdf",
            status = "COMPLETED",
            filePath = "/sdcard/Download/normal_document.pdf",
            isPrivate = false,
            privateSessionId = null
        )
        repository.insertOrUpdateDownload(normalItem)

        // Verify normal download exists
        val initialList = repository.getAllDownloadsFlow().first()
        assertTrue("Normal download should be stored in database", initialList.any { it.id == normalId })

        // Trigger private session cleanup
        downloadEngine.cleanupPrivateSession(sessionId = "private_session_abc")

        // Normal download must remain untouched
        val afterCleanupList = repository.getAllDownloadsFlow().first()
        assertEquals(1, afterCleanupList.size)
        assertEquals(normalId, afterCleanupList.first().id)
        assertEquals("https://example.com/files/normal_document.pdf", afterCleanupList.first().url)
        assertFalse(afterCleanupList.first().isPrivate)
    }

    @Test
    fun testPrivateDownloadedFileRemainsOnDiskAfterMetadataCleanup() = runTest {
        val privateId = 2002L
        val testFile = File(context.cacheDir, "private_download_file.zip").apply {
            writeText("Sample downloaded binary file content")
        }
        createdTestFiles.add(testFile)
        assertTrue("Physical test file must exist on disk before session end", testFile.exists())

        val privateItem = DownloadItem(
            id = privateId,
            title = "private_download_file.zip",
            url = "https://sensitive-site.org/archive/private_download_file.zip",
            mimeType = "application/zip",
            status = "COMPLETED",
            filePath = testFile.absolutePath,
            isPrivate = true,
            privateSessionId = "private_session_1"
        )
        repository.insertOrUpdateDownload(privateItem)

        // Verify private download was recorded
        val privateDownloads = repository.getPrivateDownloads("private_session_1")
        assertEquals(1, privateDownloads.size)
        assertEquals(privateId, privateDownloads.first().id)

        // End private session -> clean up private browsing metadata
        downloadEngine.cleanupPrivateSession(sessionId = "private_session_1")

        // 1. Browsing history metadata must be purged from database
        val afterCleanupPrivate = repository.getPrivateDownloads("private_session_1")
        assertTrue("Private download metadata must be removed on session cleanup", afterCleanupPrivate.isEmpty())

        // 2. ACTUAL PHYSICAL FILE MUST REMAIN ON DEVICE DISK
        assertTrue("Downloaded physical file must remain on disk after private session closes", testFile.exists())
        assertEquals("Sample downloaded binary file content", testFile.readText())
    }

    @Test
    fun testPrivateMetadataRedactionAtSessionEnd() = runTest {
        val privateId = 3003L
        val testFile = File(context.cacheDir, "confidential.pdf").apply {
            writeText("Confidential PDF content")
        }
        createdTestFiles.add(testFile)

        val privateItem = DownloadItem(
            id = privateId,
            title = "confidential.pdf",
            url = "https://secure-portal.com/user/99/confidential.pdf",
            mimeType = "application/pdf",
            status = "COMPLETED",
            filePath = testFile.absolutePath,
            isPrivate = true,
            privateSessionId = "private_session_redact"
        )
        repository.insertOrUpdateDownload(privateItem)

        // Run redact-only metadata cleanup
        downloadEngine.cleanupPrivateSession(sessionId = "private_session_redact", redactOnly = true)

        val allItems = repository.getAllDownloadsFlow().first()
        assertEquals(1, allItems.size)
        val item = allItems.first()
        assertEquals("[PRIVATE_DOWNLOAD]", item.url)
        assertFalse("isPrivate flag should be cleared after redaction", item.isPrivate)
        assertNull("privateSessionId should be cleared after redaction", item.privateSessionId)
        assertTrue("File on disk must still exist", testFile.exists())
    }

    @Test
    fun testActiveDownloadNotAccidentallyDeletedDuringOtherSessionCleanup() = runTest {
        // Normal download
        val normalItem = DownloadItem(
            id = 4001L,
            title = "normal.png",
            url = "https://example.com/normal.png",
            mimeType = "image/png",
            status = "RUNNING",
            isPrivate = false
        )
        // Session A private download (completed)
        val sessionAItem = DownloadItem(
            id = 4002L,
            title = "sessionA.pdf",
            url = "https://site-a.com/sessionA.pdf",
            mimeType = "application/pdf",
            status = "COMPLETED",
            isPrivate = true,
            privateSessionId = "session_A"
        )
        // Session B private download (actively running)
        val sessionBActiveItem = DownloadItem(
            id = 4003L,
            title = "sessionB_active.zip",
            url = "https://site-b.com/sessionB_active.zip",
            mimeType = "application/zip",
            status = "RUNNING",
            progress = 45,
            isPrivate = true,
            privateSessionId = "session_B"
        )

        repository.insertOrUpdateDownload(normalItem)
        repository.insertOrUpdateDownload(sessionAItem)
        repository.insertOrUpdateDownload(sessionBActiveItem)

        // Cleanup only Session A
        downloadEngine.cleanupPrivateSession(sessionId = "session_A")

        val remainingItems = repository.getAllDownloadsFlow().first()
        assertEquals(2, remainingItems.size)
        assertTrue("Normal download should remain", remainingItems.any { it.id == 4001L })
        assertTrue("Active Session B download must not be deleted", remainingItems.any { it.id == 4003L && it.status == "RUNNING" })
        assertFalse("Session A item should be deleted", remainingItems.any { it.id == 4002L })
    }
}
