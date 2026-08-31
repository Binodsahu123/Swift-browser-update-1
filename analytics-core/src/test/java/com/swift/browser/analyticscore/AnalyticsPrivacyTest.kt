package com.swift.browser.analyticscore

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyticsPrivacyTest {

    @Before
    fun setUp() {
        AnalyticsCore.clearAll()
    }

    @Test
    fun testPrivatePageTrackingRedaction() {
        val sensitiveUrl = "https://secure.bank.com/account?secret_token=abc12345"
        
        // Track private page load
        AnalyticsCore.trackPageLoad(
            url = sensitiveUrl,
            isIncognito = true,
            context = AnalyticsContext.PRIVATE
        )

        val events = AnalyticsCore.browserAnalytics.events.value
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("page_load", event.eventName)
        assertEquals(true, event.params["is_incognito"])
        assertEquals("[PRIVATE_BROWSING]", event.params["url"])
        assertFalse("Full private URL must not be present in analytics event", event.params["url"].toString().contains("bank.com"))
        assertFalse("Private token must not be leaked", event.params["url"].toString().contains("abc12345"))
    }

    @Test
    fun testPrivateNavigationTraceRedaction() {
        val privateUrl = "https://private-forum.org/threads/1234?user=anonymous"
        
        // Track navigation in private context
        AnalyticsCore.trackNavigation(
            url = privateUrl,
            loadDurationMs = 120L,
            httpCode = 200,
            isSuccess = true,
            context = AnalyticsContext.PRIVATE
        )

        val traces = AnalyticsCore.navigationAnalytics.navigationTraces.value
        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals("[PRIVATE_BROWSING]", trace.rawUrl)
        assertEquals("[PRIVATE_BROWSING]", trace.sanitizedUrl)
        assertEquals("[PRIVATE_ORIGIN]", trace.domain)
        assertEquals(120L, trace.loadDurationMs)

        // Domain visits map must NOT contain private domain
        val domainVisits = AnalyticsCore.navigationAnalytics.domainVisits.value
        assertTrue("Domain visits must not track private origins", domainVisits.isEmpty())
    }

    @Test
    fun testNormalNavigationUnaffected() {
        val normalUrl = "https://kotlinlang.org/docs/home.html"

        AnalyticsCore.trackNavigation(
            url = normalUrl,
            loadDurationMs = 85L,
            httpCode = 200,
            isSuccess = true,
            context = AnalyticsContext.NORMAL
        )

        val traces = AnalyticsCore.navigationAnalytics.navigationTraces.value
        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(normalUrl, trace.rawUrl)
        assertEquals("kotlinlang.org", trace.domain)

        val domainVisits = AnalyticsCore.navigationAnalytics.domainVisits.value
        assertEquals(1, domainVisits["kotlinlang.org"])
    }

    @Test
    fun testPrivateSearchQueryRedaction() {
        val query = "how to cure private ailment"

        // Track search query with private context
        AnalyticsCore.trackSearchQuery(
            query = query,
            searchEngine = "DuckDuckGo",
            context = AnalyticsContext.PRIVATE
        )

        val events = AnalyticsCore.browserAnalytics.events.value
        val searchEvent = events.firstOrNull { it.eventName == "search_query" }
        assertNotNull(searchEvent)
        assertEquals(true, searchEvent!!.params["is_incognito"])
        assertEquals("DuckDuckGo", searchEvent.params["engine"])
        assertNull("Query length should not be exposed in private search event", searchEvent.params["query_length"])
        assertFalse("Query string must never be in params", searchEvent.params.values.any { it.toString().contains("ailment") })
    }

    @Test
    fun testCrashDiagnosticsRedaction() {
        val sensitiveCrashMsg = "Failed to load https://internal.corp.com/auth?token=supersecret123 for user test@example.com"
        AnalyticsCore.crashAnalytics.recordError(sensitiveCrashMsg)

        val reports = AnalyticsCore.crashAnalytics.crashReports.value
        assertEquals(1, reports.size)
        val report = reports.first()
        assertFalse("Crash reports must not contain raw auth tokens", report.message.contains("supersecret123"))
        assertFalse("Crash reports must redact email addresses", report.message.contains("test@example.com"))
        assertTrue("Crash reports must redact email address pattern", report.message.contains("[REDACTED_EMAIL]"))
    }
}
