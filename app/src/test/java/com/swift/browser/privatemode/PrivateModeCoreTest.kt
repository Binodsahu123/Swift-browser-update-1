package com.swift.browser.privatemode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateModeCoreTest {

    private lateinit var context: Context
    private lateinit var engine: PrivateModeEngineImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PrivateModeEngineProvider.resetForTesting()
        engine = PrivateModeEngineImpl.getInstance(context)
    }

    @Test
    fun testMultipleSessions() = runBlocking {
        val session1 = engine.openSession()
        val session2 = engine.openSession()

        assertTrue(session1.sessionId != session2.sessionId)
        assertEquals(PrivateModeSessionState.ACTIVE, session1.state)
        assertEquals(PrivateModeSessionState.ACTIVE, session2.state)

        engine.attachTab(session1.sessionId, "tab_101")
        engine.attachTab(session2.sessionId, "tab_102")

        val retrieved1 = engine.getSession(session1.sessionId)
        val retrieved2 = engine.getSession(session2.sessionId)

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertTrue(retrieved1!!.privateTabIds.contains("tab_101"))
        assertTrue(retrieved2!!.privateTabIds.contains("tab_102"))
        assertEquals(2, engine.state.value.sessions.size)
        assertEquals(2, engine.state.value.totalPrivateTabsCount)
    }

    @Test
    fun testSameSessionMultipleTabs() = runBlocking {
        val session = engine.openSession()
        val sId = session.sessionId

        assertTrue(engine.attachTab(sId, "tab_A"))
        assertTrue(engine.attachTab(sId, "tab_B"))
        assertTrue(engine.attachTab(sId, "tab_C"))

        val updatedSession = engine.getSession(sId)
        assertNotNull(updatedSession)
        assertEquals(3, updatedSession!!.privateTabIds.size)
        assertTrue(updatedSession.privateTabIds.containsAll(listOf("tab_A", "tab_B", "tab_C")))
        assertEquals(3, engine.state.value.totalPrivateTabsCount)
    }

    @Test
    fun testLastTabClose() = runBlocking {
        val session = engine.openSession()
        val sId = session.sessionId

        engine.attachTab(sId, "tab_1")
        engine.attachTab(sId, "tab_2")

        // Detach first tab
        engine.detachTab("tab_1")
        assertNotNull(engine.getSession(sId))
        assertEquals(1, engine.getSession(sId)!!.privateTabIds.size)

        // Detach last tab - must close the session automatically
        engine.detachTab("tab_2")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(300)

        assertNull(engine.getSession(sId))
        assertFalse(engine.state.value.sessions.containsKey(sId))
    }

    @Test
    fun testDuplicateAttachDetach() = runBlocking {
        val session1 = engine.openSession()
        val session2 = engine.openSession()

        // Attach tab_X to session 1
        assertTrue(engine.attachTab(session1.sessionId, "tab_X"))
        
        // Duplicate attach to same session
        assertTrue(engine.attachTab(session1.sessionId, "tab_X"))
        assertEquals(1, engine.getSession(session1.sessionId)!!.privateTabIds.size)

        // Re-attach tab_X to session 2 (moves tab)
        assertTrue(engine.attachTab(session2.sessionId, "tab_X"))
        assertFalse(engine.getSession(session1.sessionId)!!.privateTabIds.contains("tab_X"))
        assertTrue(engine.getSession(session2.sessionId)!!.privateTabIds.contains("tab_X"))

        // Duplicate detach
        assertTrue(engine.detachTab("tab_X"))
        assertFalse(engine.detachTab("tab_X")) // Second call returns false safely
    }

    @Test
    fun testStaleSession() = runBlocking {
        val session = engine.openSession()
        assertEquals(PrivateModeSessionState.ACTIVE, session.state)

        // Run cleanup on session without any attached tabs
        engine.cleanupOrphans()

        // Stale session without tabs should be closed/cleaned
        assertNull(engine.getSession(session.sessionId))
    }

    @Test
    fun testProcessRestartCleanup() = runBlocking {
        val session1 = engine.openSession()
        val session2 = engine.openSession()

        engine.attachTab(session1.sessionId, "tab_P1")
        engine.attachTab(session2.sessionId, "tab_P2")

        engine.closeAllSessions()

        assertFalse(engine.state.value.isActive)
        assertTrue(engine.state.value.sessions.isEmpty())
        assertTrue(engine.state.value.tabToSessionMap.isEmpty())
        assertEquals(0, engine.state.value.totalPrivateTabsCount)

        // Cleanup orphans post restart
        engine.cleanupOrphans()
        assertFalse(engine.isPrivateModeActive())
    }

    @Test
    fun testBiometricLockAndUnlock() = runBlocking {
        // By default biometric is required and locked
        engine.setBiometricRequired(true)
        engine.lockPrivateTabs()
        assertFalse(engine.canAccessPrivateTabs())
        assertFalse(engine.state.value.isBiometricUnlocked)

        // Unlock private tabs
        engine.unlockPrivateTabs()
        assertTrue(engine.canAccessPrivateTabs())
        assertTrue(engine.state.value.isBiometricUnlocked)

        // Lock again
        engine.lockPrivateTabs()
        assertFalse(engine.canAccessPrivateTabs())
        assertFalse(engine.state.value.isBiometricUnlocked)

        // Disable biometric requirement
        engine.setBiometricRequired(false)
        assertTrue(engine.canAccessPrivateTabs())
    }

    @Test
    fun testAutoPurgeOnBiometricTimeoutAndExit() = runBlocking {
        engine.isAutoPurgeOnTimeoutOrExit = true
        assertTrue(engine.isAutoPurgeOnTimeoutOrExit)
        assertTrue(engine.state.value.isAutoPurgeOnTimeoutOrExit)

        // Open a session
        val session = engine.openSession()
        engine.attachTab(session.sessionId, "tab_private_1")

        // Trigger biometric timeout
        engine.onBiometricTimeout()
        assertFalse(engine.canAccessPrivateTabs())

        // Trigger app exit
        engine.onAppExit()

        // Disable setting
        engine.isAutoPurgeOnTimeoutOrExit = false
        assertFalse(engine.isAutoPurgeOnTimeoutOrExit)
        assertFalse(engine.state.value.isAutoPurgeOnTimeoutOrExit)
    }
}
