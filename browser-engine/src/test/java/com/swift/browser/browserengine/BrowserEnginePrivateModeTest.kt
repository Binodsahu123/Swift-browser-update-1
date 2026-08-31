package com.swift.browser.browserengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swift.browser.privatemode.PrivateModeEngineImpl
import com.swift.browser.privatemode.PrivateModeEngineProvider
import com.swift.browser.tabengine.model.TabModel
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
class BrowserEnginePrivateModeTest {

    private lateinit var context: Context
    private lateinit var controller: BrowserWebViewController
    private lateinit var privateEngine: PrivateModeEngineImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PrivateModeEngineProvider.resetForTesting()
        privateEngine = PrivateModeEngineImpl.getInstance(context)
        controller = BrowserWebViewController(context)
    }

    @Test
    fun testNormalWebViewCreation() = runBlocking {
        val tabId = "tab_normal_1"
        val tabModel = TabModel(id = tabId, url = "https://example.com", isPrivate = false)

        val webView = controller.createAndConfigureWebView(
            tabId = tabId,
            config = BrowserWebViewController.WebViewConfiguration(isIncognito = false),
            getTab = { if (it == tabId) tabModel else null },
            updateTabModel = { _, _ -> },
            triggerTabUpdatedEvent = { _, _ -> },
            extensionSetup = { _, _ -> },
            injectContentScripts = { _, _, _ -> },
            flushCookies = {},
            permissionEngine = null,
            uiCallbacks = object : BrowserWebViewController.WebViewUiCallbacks {}
        )

        assertNotNull(webView)
        assertFalse(webView.isPrivate)
        assertNull(webView.privateSessionId)
        assertNull(webView.profileName)
    }

    @Test
    fun testPrivateWebViewCreationWithSessionContract() = runBlocking {
        val session = privateEngine.openSession()
        val sId = session.sessionId
        val tabId = "tab_priv_1"
        val tabModel = TabModel(id = tabId, url = "https://example.com", isPrivate = true, privateSessionId = sId)

        val webView = controller.createAndConfigureWebView(
            tabId = tabId,
            config = BrowserWebViewController.WebViewConfiguration(isPrivate = true, privateSessionId = sId),
            getTab = { if (it == tabId) tabModel else null },
            updateTabModel = { _, _ -> },
            triggerTabUpdatedEvent = { _, _ -> },
            extensionSetup = { _, _ -> },
            injectContentScripts = { _, _, _ -> },
            flushCookies = {},
            permissionEngine = null,
            uiCallbacks = object : BrowserWebViewController.WebViewUiCallbacks {}
        )

        assertNotNull(webView)
        assertTrue(webView.isPrivate)
        assertEquals(sId, webView.privateSessionId)
        assertEquals(session.profileName, webView.profileName)

        // Session must track the tab
        val updatedSession = privateEngine.getSession(sId)
        assertNotNull(updatedSession)
        assertTrue(updatedSession!!.privateTabIds.contains(tabId))
    }

    @Test
    fun testTwoPrivateSessionsIsolated() = runBlocking {
        val session1 = privateEngine.openSession()
        val session2 = privateEngine.openSession()

        val tab1Id = "tab_s1"
        val tab2Id = "tab_s2"

        val webView1 = controller.createAndConfigureWebView(
            tabId = tab1Id,
            config = BrowserWebViewController.WebViewConfiguration(isPrivate = true, privateSessionId = session1.sessionId),
            getTab = { if (it == tab1Id) TabModel(id = tab1Id, url = "https://a.com", isPrivate = true, privateSessionId = session1.sessionId) else null },
            updateTabModel = { _, _ -> },
            triggerTabUpdatedEvent = { _, _ -> },
            extensionSetup = { _, _ -> },
            injectContentScripts = { _, _, _ -> },
            flushCookies = {},
            permissionEngine = null,
            uiCallbacks = object : BrowserWebViewController.WebViewUiCallbacks {}
        )

        val webView2 = controller.createAndConfigureWebView(
            tabId = tab2Id,
            config = BrowserWebViewController.WebViewConfiguration(isPrivate = true, privateSessionId = session2.sessionId),
            getTab = { if (it == tab2Id) TabModel(id = tab2Id, url = "https://b.com", isPrivate = true, privateSessionId = session2.sessionId) else null },
            updateTabModel = { _, _ -> },
            triggerTabUpdatedEvent = { _, _ -> },
            extensionSetup = { _, _ -> },
            injectContentScripts = { _, _, _ -> },
            flushCookies = {},
            permissionEngine = null,
            uiCallbacks = object : BrowserWebViewController.WebViewUiCallbacks {}
        )

        assertTrue(webView1.profileName != webView2.profileName)
        assertEquals(session1.sessionId, webView1.privateSessionId)
        assertEquals(session2.sessionId, webView2.privateSessionId)
    }

    @Test
    fun testWebViewDestroyedCleanup() = runBlocking {
        val session = privateEngine.openSession()
        val tabId = "tab_destroy"

        val webView = controller.createAndConfigureWebView(
            tabId = tabId,
            config = BrowserWebViewController.WebViewConfiguration(isPrivate = true, privateSessionId = session.sessionId),
            getTab = { TabModel(id = tabId, url = "https://example.com", isPrivate = true, privateSessionId = session.sessionId) },
            updateTabModel = { _, _ -> },
            triggerTabUpdatedEvent = { _, _ -> },
            extensionSetup = { _, _ -> },
            injectContentScripts = { _, _, _ -> },
            flushCookies = {},
            permissionEngine = null,
            uiCallbacks = object : BrowserWebViewController.WebViewUiCallbacks {}
        )

        webView.destroy()
        // Must not crash or corrupt session engine state
        assertNotNull(privateEngine.getSession(session.sessionId))
    }
}
