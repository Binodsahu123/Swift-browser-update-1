package com.swift.browser.browserengine

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.swift.browser.permissionengine.PermissionEngineProvider
import com.swift.browser.permissionengine.PermissionRequestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserEngineAndroidTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPermissionEngineInitialization() {
        val permissionEngine = PermissionEngineProvider.get(context)
        assertNotNull("PermissionEngine instance must not be null", permissionEngine)
        val domain = permissionEngine.getDomain("https://sub.example.com/path")
        assertEquals("Domain extraction should return top host domain", "sub.example.com", domain)
    }

    @Test
    fun testNavigationRequestConstruction() {
        val request = NavigationRequest(
            tabId = "tab_test_1",
            url = "https://m.youtube.com/watch?v=123",
            source = NavigationSource.USER_TYPED
        )
        assertEquals("tab_test_1", request.tabId)
        assertEquals("https://m.youtube.com/watch?v=123", request.url)
        assertEquals(NavigationSource.USER_TYPED, request.source)
    }

    @Test
    fun testPermissionRequestContext() {
        val permContext = PermissionRequestContext(
            requestId = "req_test_123",
            tabId = "tab_1",
            origin = "https://youtube.com",
            pageUrl = "https://youtube.com/watch?v=xyz",
            isIncognito = false
        )
        assertEquals("req_test_123", permContext.requestId)
        assertEquals("tab_1", permContext.tabId)
        assertEquals("https://youtube.com", permContext.origin)
        assertEquals("https://youtube.com/watch?v=xyz", permContext.pageUrl)
    }
}
