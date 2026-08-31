package com.swift.browser.desktopengine

import com.swift.browser.desktopengine.rules.DesktopHostNormalizer
import com.swift.browser.desktopengine.useragent.UserAgentManager
import org.junit.Assert.*
import org.junit.Test

class DesktopEngineUnitTest {

    @Test
    fun testHostNormalizationForDesktop() {
        val canonicalFb = DesktopHostNormalizer.getCanonicalHost("m.facebook.com")
        assertEquals("facebook.com", canonicalFb)

        val canonicalWiki = DesktopHostNormalizer.getCanonicalHost("en.m.wikipedia.org")
        assertEquals("wikipedia.org", canonicalWiki)

        val canonicalReddit = DesktopHostNormalizer.getCanonicalHost("www.reddit.com")
        assertEquals("reddit.com", canonicalReddit)
    }

    @Test
    fun testUserAgentSelection() {
        val appleUa = UserAgentManager.getDesktopUserAgent("developer.apple.com")
        assertTrue(appleUa.contains("Windows NT 10.0") && appleUa.contains("Chrome"))

        val genericUa = UserAgentManager.getDesktopUserAgent("google.com")
        assertTrue(genericUa.contains("Windows NT 10.0") && genericUa.contains("Chrome"))

        val mobileUa = UserAgentManager.getMobileUserAgent()
        assertTrue(mobileUa.contains("Mobile Safari") && mobileUa.contains("Android"))
    }
}
