package com.swift.browser.securityengine

import com.swift.browser.securityengine.manager.SecurityCacheManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SecurityEnginePrivateModeTest {

    private lateinit var cacheManager: SecurityCacheManager
    private val securityEngine = SwiftSecurityEngine

    @Before
    fun setUp() {
        cacheManager = SecurityCacheManager()
        SwiftSecurityEngine.unwhitelistDomain("phishing-example.com")
        SwiftSecurityEngine.unwhitelistDomain("malware-example.com")
    }

    @Test
    fun testPrivateNavigationIsSecureAndSafeBrowsingWorks() {
        val safeUrl = "https://private-safe-site.com/secret/path?token=12345"
        val isSafe = securityEngine.isUrlSafe(safeUrl, isPrivate = true)
        assertTrue("Private URL check must remain active and mark safe sites as safe", isSafe)

        val unsafeUrl = "https://phishing-test.org/login?secret_user=admin"
        val isUnsafe = securityEngine.isUrlSafe(unsafeUrl, isPrivate = true)
        assertFalse("Private navigation must still enforce Safe Browsing and block threats", isUnsafe)
    }

    @Test
    fun testPrivateUrlPathNotPersistedInNormalCache() {
        val privateUrl = "https://sensitive-bank.com/account/dashboard?session=xyz987"
        
        cacheManager.cacheSafety(privateUrl, isSafe = true, isPrivate = true)

        // Normal cache lookup for full private URL path should return null
        val normalCacheResult = cacheManager.getCachedSafety(privateUrl, isPrivate = false)
        assertNull("Full private URL path must NOT be persisted in normal security cache", normalCacheResult)

        // Private cache lookup should return cached result
        val privateCacheResult = cacheManager.getCachedSafety(privateUrl, isPrivate = true)
        assertEquals("Private safety cache returns cached result", true, privateCacheResult)
    }

    @Test
    fun testNormalSecurityDataUnchanged() {
        val normalUrl = "https://normal-shopping.com/product/101"
        cacheManager.cacheSafety(normalUrl, isSafe = true, isPrivate = false)

        val cached = cacheManager.getCachedSafety(normalUrl, isPrivate = false)
        assertEquals("Normal security cache must function unchanged", true, cached)
    }

    @Test
    fun testSecurityStateSanitizesPrivateUrls() {
        val privateUrl = "https://private-forum.org/user/profile?id=999"
        val state = securityEngine.checkSecurityState(privateUrl, isPrivate = true)

        assertFalse("State currentUrl should not contain private URL path/query", state.currentUrl.contains("/user/profile"))
        assertTrue("State currentUrl should contain host or redacted placeholder", state.currentUrl.contains("https://private-forum.org/[PRIVATE_PAGE]"))
    }
}
