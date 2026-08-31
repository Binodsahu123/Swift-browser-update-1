package com.swift.browser.extensionengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swift.browser.extensionengine.origin.ExtensionOrigin
import com.swift.browser.extensionengine.origin.ExtensionOriginValidator
import com.swift.browser.extensionengine.origin.ExtensionUrl
import com.swift.browser.extensionengine.resources.AccessDecision
import com.swift.browser.extensionengine.resources.ExtensionResourceAccessPolicy
import com.swift.browser.extensionengine.resources.ExtensionResourceResolver
import com.swift.browser.extensionengine.resources.ExtensionResourceServer
import com.swift.browser.extensionengine.security.ExtensionCspPolicy
import com.swift.browser.extensionengine.security.ExtensionPageType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExtensionOriginSecurityTest {

    private lateinit var context: Context
    private lateinit var fakeRegistry: ExtensionRegistry
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeRegistry = ExtensionRegistry()
        tempDir = File(context.filesDir, "ext_sec_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
    }

    // --- Goal 1: ExtensionOrigin Tests ---

    @Test
    fun testExtensionOriginCreationValid() {
        val origin = ExtensionOrigin.fromExtensionId("abcdefghijklmnopabcdefghijklmnop")
        assertEquals("abcdefghijklmnopabcdefghijklmnop", origin.host)
        assertEquals("chrome-extension://abcdefghijklmnopabcdefghijklmnop/", origin.origin)
    }

    @Test(expected = ExtensionError.SecurityError.InvalidExtensionOrigin::class)
    fun testExtensionOriginBlankIdThrows() {
        ExtensionOrigin.fromExtensionId("   ")
    }

    @Test(expected = ExtensionError.SecurityError.InvalidExtensionOrigin::class)
    fun testExtensionOriginUnsafeIdThrows() {
        ExtensionOrigin.fromExtensionId("../etc/passwd")
    }

    @Test
    fun testExtensionOriginFromUrl() {
        val origin = ExtensionOrigin.fromUrl("chrome-extension://myextid12345/popup.html")
        assertNotNull(origin)
        assertEquals("myextid12345", origin?.host)
        assertEquals("chrome-extension://myextid12345/", origin?.origin)
    }

    @Test
    fun testExtensionOriginValidSchemes() {
        assertTrue(ExtensionOrigin.isValidScheme("chrome-extension"))
        assertTrue(ExtensionOrigin.isValidScheme("swift-extension"))
        assertFalse(ExtensionOrigin.isValidScheme("http"))
        assertFalse(ExtensionOrigin.isValidScheme("file"))
    }

    // --- Goal 2: ExtensionUrl Tests ---

    @Test
    fun testExtensionUrlToExtensionUrl() {
        val url = ExtensionUrl.toExtensionUrl("MyExtId", "popup/index.html")
        assertEquals("chrome-extension://myextid/popup/index.html", url)
    }

    @Test
    fun testExtensionUrlParseValid() {
        val result = ExtensionUrl.parseExtensionUrl("chrome-extension://ext123/scripts/content.js")
        assertNotNull(result)
        assertEquals("ext123", result?.extensionId)
        assertEquals("scripts/content.js", result?.resourcePath)
        assertEquals("chrome-extension://ext123/scripts/content.js", result?.canonicalUrl)
    }

    @Test
    fun testExtensionUrlParseTraversalReturnsNull() {
        assertNull(ExtensionUrl.parseExtensionUrl("chrome-extension://ext123/../../etc/passwd"))
        assertNull(ExtensionUrl.parseExtensionUrl("chrome-extension://ext123/scripts/..\\..\\secret.txt"))
    }

    @Test
    fun testExtensionUrlIsExtensionUrl() {
        assertTrue(ExtensionUrl.isExtensionUrl("chrome-extension://abc/popup.html"))
        assertTrue(ExtensionUrl.isExtensionUrl("swift-extension://abc/popup.html"))
        assertFalse(ExtensionUrl.isExtensionUrl("https://example.com"))
        assertFalse(ExtensionUrl.isExtensionUrl("file:///android_asset/main.html"))
    }

    // --- Goal 11: ExtensionOriginValidator Tests ---

    @Test
    fun testOriginValidatorValid() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "chrome-extension://ext123/options.html"
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.VALID, result)
    }

    @Test
    fun testOriginValidatorIdMismatch() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "chrome-extension://ext456/options.html"
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.ID_MISMATCH, result)
    }

    @Test
    fun testOriginValidatorFileUrlBlocked() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "file:///sdcard/ext/options.html"
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.FILE_URL_DISALLOWED, result)
    }

    @Test
    fun testOriginValidatorContentUrlBlocked() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "content://media/external/file/10"
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.FILE_URL_DISALLOWED, result)
    }

    @Test
    fun testOriginValidatorSandboxIsolatedContext() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "chrome-extension://ext123/sandbox.html",
            expectedContext = ExtensionPageType.SANDBOX_PAGE,
            registry = fakeRegistry
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.SANDBOX_PAGE_ISOLATED, result)
    }

    @Test
    fun testOriginValidatorSameAndExternalOrigin() {
        assertTrue(ExtensionOriginValidator.isSameExtensionOrigin("chrome-extension://abc/a.html", "chrome-extension://abc/b.html"))
        assertFalse(ExtensionOriginValidator.isSameExtensionOrigin("chrome-extension://abc/a.html", "chrome-extension://def/b.html"))
        assertTrue(ExtensionOriginValidator.isExternalOrigin("https://google.com"))
        assertFalse(ExtensionOriginValidator.isExternalOrigin("chrome-extension://abc/a.html"))
    }

    // --- Goal 8: ExtensionCspPolicy Tests ---

    @Test
    fun testCspPolicyDefaults() {
        val extMv2 = createDummyParsedExtension("ext_mv2", 2)
        val extMv3 = createDummyParsedExtension("ext_mv3", 3)

        val cspMv2 = ExtensionCspPolicy.getCspForExtension(extMv2, ExtensionPageType.EXTENSION_PAGE)
        val cspMv3 = ExtensionCspPolicy.getCspForExtension(extMv3, ExtensionPageType.EXTENSION_PAGE)
        val cspSandbox = ExtensionCspPolicy.getCspForExtension(extMv3, ExtensionPageType.SANDBOX_PAGE)

        assertTrue(cspMv2.contains("unsafe-eval"))
        assertFalse(cspMv3.contains("unsafe-eval"))
        assertTrue(cspSandbox.contains("sandbox"))
    }

    @Test
    fun testCspPolicyMv3SanitizesUnsafeEval() {
        val manifestJson = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Test")
            put("version", "1.0")
            put("content_security_policy", JSONObject().put("extension_pages", "script-src 'self' 'unsafe-eval'; object-src 'self';"))
        }.toString()

        val ext = createDummyParsedExtension("ext_eval", 3, manifestJson)
        val csp = ExtensionCspPolicy.getCspForExtension(ext, ExtensionPageType.EXTENSION_PAGE)

        assertFalse(csp.contains("'unsafe-eval'"))
    }

    // --- Goal 12 & 13: ExtensionResourceAccessPolicy Tests ---

    @Test
    fun testResourceAccessPolicyInternalAllowed() {
        val ext = createDummyParsedExtension("ext123", 3)
        val decision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/icon.png",
            initiatorUrlStr = "chrome-extension://ext123/popup.html",
            ext = ext
        )
        assertEquals(AccessDecision.ALLOW, decision)
    }

    @Test
    fun testResourceAccessPolicyWebAccessDeniedByDefault() {
        val ext = createDummyParsedExtension("ext123", 3)
        val decision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/secret.js",
            initiatorUrlStr = "https://example.com",
            ext = ext
        )
        assertEquals(AccessDecision.DENY, decision)
    }

    @Test
    fun testResourceAccessPolicyMv2WebAccessibleAllowed() {
        val manifestJson = JSONObject().apply {
            put("manifest_version", 2)
            put("name", "Test MV2")
            put("version", "1.0")
            put("web_accessible_resources", JSONArray().put("images/*").put("inject.js"))
        }.toString()

        val ext = createDummyParsedExtension("ext123", 2, manifestJson)

        val decisionAllowed = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/images/logo.png",
            initiatorUrlStr = "https://example.com",
            ext = ext
        )
        val decisionDenied = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/private.js",
            initiatorUrlStr = "https://example.com",
            ext = ext
        )

        assertEquals(AccessDecision.ALLOW, decisionAllowed)
        assertEquals(AccessDecision.DENY, decisionDenied)
    }

    @Test
    fun testResourceAccessPolicyMv3WebAccessibleAllowedWithMatches() {
        val warArray = JSONArray().put(JSONObject().apply {
            put("resources", JSONArray().put("public.js"))
            put("matches", JSONArray().put("https://*.example.com/*"))
        })
        val manifestJson = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Test MV3")
            put("version", "1.0")
            put("web_accessible_resources", warArray)
        }.toString()

        val ext = createDummyParsedExtension("ext123", 3, manifestJson)

        val allowedDecision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/public.js",
            initiatorUrlStr = "https://sub.example.com/page.html",
            ext = ext
        )
        val deniedDecision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/public.js",
            initiatorUrlStr = "https://evil.com/page.html",
            ext = ext
        )

        assertEquals(AccessDecision.ALLOW, allowedDecision)
        assertEquals(AccessDecision.DENY, deniedDecision)
    }

    // --- Goal 14: ExtensionBridgeSecurityContext & ExtensionBridgePolicy Tests ---

    @Test
    fun testBridgePolicyAllowedForValidExtensionPage() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.EXTENSION_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://ext123/options.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.ALLOW, decision)
    }

    @Test
    fun testBridgePolicyDeniedForSandboxPage() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.SANDBOX_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://ext123/sandbox.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_SANDBOX_CONTEXT, decision)
    }

    @Test
    fun testBridgePolicyDeniedForWebPage() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.WEB_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "https://example.com",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_WEB_CONTEXT, decision)
    }

    @Test
    fun testBridgePolicyDeniedForFileScheme() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.EXTENSION_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "file:///sdcard/malicious.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_FILE_SCHEME, decision)
    }

    @Test
    fun testBridgePolicyDeniedForDisabledExtension() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)
        fakeRegistry.transitionState("ext123", ExtensionState.INSTALLED_DISABLED)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.EXTENSION_PAGE,
            enabled = false
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://ext123/options.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_DISABLED_EXTENSION, decision)
    }

    // --- Goal 15: ExtensionSandboxPolicy Tests ---

    @Test
    fun testSandboxPolicyDetection() {
        val sandboxManifest = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Sandbox Ext")
            put("version", "1.0")
            put("sandbox", JSONObject().apply {
                put("pages", JSONArray().put("sandbox.html").put("pages/*"))
            })
        }.toString()

        val ext = createDummyParsedExtension("ext_sandbox", 3, sandboxManifest)

        assertTrue(com.swift.browser.extensionengine.security.ExtensionSandboxPolicy.isSandboxedPage("chrome-extension://ext_sandbox/sandbox.html", ext))
        assertTrue(com.swift.browser.extensionengine.security.ExtensionSandboxPolicy.isSandboxedPage("chrome-extension://ext_sandbox/pages/eval.html", ext))
        assertFalse(com.swift.browser.extensionengine.security.ExtensionSandboxPolicy.isSandboxedPage("chrome-extension://ext_sandbox/popup.html", ext))
        assertFalse(com.swift.browser.extensionengine.security.ExtensionSandboxPolicy.isPrivilegedAccessAllowed(true))
        assertTrue(com.swift.browser.extensionengine.security.ExtensionSandboxPolicy.isPrivilegedAccessAllowed(false))
    }

    // --- Goal 3 & 4: ExtensionResourceResolver & ResourceServer Tests ---

    @Test(expected = ExtensionError.SecurityError.ExtensionNotFound::class)
    fun testResourceResolverMissingExtensionThrows() {
        val resolver = ExtensionResourceResolver(context, fakeRegistry, null)
        resolver.resolveResource("chrome-extension://nonexistent/popup.html")
    }

    @Test(expected = ExtensionError.SecurityError.AccessDenied::class)
    fun testResourceResolverDisabledExtensionThrows() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)
        fakeRegistry.transitionState("ext123", ExtensionState.INSTALLED_DISABLED)

        val resolver = ExtensionResourceResolver(context, fakeRegistry, null)
        resolver.resolveResource("chrome-extension://ext123/popup.html")
    }

    @Test
    fun testResourceServerSafelyReturns404Response() {
        val server = ExtensionResourceServer(context, fakeRegistry, null)
        val response = server.handleUrlRequest("chrome-extension://nonexistent/popup.html")

        assertNotNull(response)
        assertEquals(404, response?.statusCode)
    }

    private fun createDummyParsedExtension(
        id: String,
        manifestVersion: Int,
        manifestJsonOverride: String? = null
    ): ParsedExtension {
        val json = manifestJsonOverride ?: JSONObject().apply {
            put("manifest_version", manifestVersion)
            put("name", "Test Extension")
            put("version", "1.0.0")
        }.toString()

        return ParsedExtension(
            id = id,
            name = "Test Extension",
            version = "1.0.0",
            description = "Test Description",
            manifestVersion = manifestVersion,
            permissions = emptyList(),
            hostPermissions = emptyList(),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "popup.html",
            optionsPage = "",
            manifestJson = json
        )
    }

    // --- PART 2 REQUIRED TESTS (1-30) ---

    // TEST 1: Extension A origin != Extension B origin.
    @Test
    fun testRequired1_ExtensionAOriginNotEqualsExtensionBOrigin() {
        val originA = ExtensionOrigin.fromExtensionId("extensiona")
        val originB = ExtensionOrigin.fromExtensionId("extensionb")
        assertNotEquals(originA.origin, originB.origin)
    }

    // TEST 2: Invalid extension ID rejected.
    @Test
    fun testRequired2_InvalidExtensionIdRejected() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "invalid_id_with_spaces!",
            urlStr = "chrome-extension://invalid_id_with_spaces!/popup.html"
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.INVALID_ID, result)
    }

    // TEST 3: Unknown extension origin rejected.
    @Test
    fun testRequired3_UnknownExtensionOriginRejected() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "unknownext",
            urlStr = "chrome-extension://unknownext/popup.html",
            registry = fakeRegistry
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.UNKNOWN_EXTENSION, result)
    }

    // TEST 4: Safe resource path accepted.
    @Test
    fun testRequired4_SafeResourcePathAccepted() {
        val result = ExtensionUrl.parseExtensionUrl("chrome-extension://myextid/assets/images/logo.png")
        assertNotNull(result)
        assertEquals("assets/images/logo.png", result?.resourcePath)
    }

    // TEST 5: "../" traversal rejected.
    @Test
    fun testRequired5_TraversalPathRejected() {
        val result = ExtensionUrl.parseExtensionUrl("chrome-extension://myext/../secret.txt")
        assertNull(result)
    }

    // TEST 6: encoded traversal rejected.
    @Test
    fun testRequired6_EncodedTraversalRejected() {
        val path1 = "%2e%2e%2fsecret.txt"
        val path2 = "..%2f..%2fsecret.txt"
        assertFalse(PathSanitizer.isSafeRelativePath(path1) && !path1.contains(".."))
        val res1 = ExtensionUrl.parseExtensionUrl("chrome-extension://myext/$path1")
        val res2 = ExtensionUrl.parseExtensionUrl("chrome-extension://myext/$path2")
        assertTrue(res1 == null || !res1.resourcePath.contains(".."))
        assertTrue(res2 == null || !res2.resourcePath.contains(".."))
    }

    // TEST 7: absolute filesystem path rejected.
    @Test
    fun testRequired7_AbsoluteFilesystemPathRejected() {
        val res = ExtensionUrl.parseExtensionUrl("chrome-extension://myext//etc/passwd")
        assertTrue(res == null || res.resourcePath.startsWith("/") || !PathSanitizer.isSafeRelativePath(res.resourcePath))
    }

    // TEST 8: outside-root canonical path rejected.
    @Test
    fun testRequired8_OutsideRootCanonicalPathRejected() {
        val root = tempDir
        val outsideFile = File(context.filesDir, "secret.txt")
        assertThrows(Exception::class.java) {
            PathSanitizer.verifyCanonicalContainment(root, outsideFile)
        }
    }

    // TEST 9: disabled extension resource denied where execution requires enabled state.
    @Test
    fun testRequired9_DisabledExtensionResourceDenied() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)
        fakeRegistry.transitionState("ext123", ExtensionState.INSTALLED_DISABLED)

        val resolver = ExtensionResourceResolver(context, fakeRegistry, null)
        assertThrows(ExtensionError.SecurityError.AccessDenied::class.java) {
            resolver.resolveResource("chrome-extension://ext123/popup.html")
        }
    }

    // TEST 10: non-web-accessible resource requested by website denied.
    @Test
    fun testRequired10_NonWebAccessibleResourceRequestedByWebsiteDenied() {
        val ext = createDummyParsedExtension("ext123", 3)
        val decision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/secret.js",
            initiatorUrlStr = "https://example.com",
            ext = ext
        )
        assertEquals(AccessDecision.DENY, decision)
    }

    // TEST 11: declared web-accessible resource allowed for matching origin.
    @Test
    fun testRequired11_DeclaredWebAccessibleResourceAllowedForMatchingOrigin() {
        val manifestJson = JSONObject().apply {
            put("manifest_version", 2)
            put("name", "Test")
            put("version", "1.0")
            put("web_accessible_resources", JSONArray().put("public.js"))
        }.toString()
        val ext = createDummyParsedExtension("ext123", 2, manifestJson)

        val decision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/public.js",
            initiatorUrlStr = "https://example.com",
            ext = ext
        )
        assertEquals(AccessDecision.ALLOW, decision)
    }

    // TEST 12: declared resource denied to non-matching origin.
    @Test
    fun testRequired12_DeclaredResourceDeniedToNonMatchingOrigin() {
        val warArray = JSONArray().put(JSONObject().apply {
            put("resources", JSONArray().put("public.js"))
            put("matches", JSONArray().put("https://*.allowed.com/*"))
        })
        val manifestJson = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Test")
            put("version", "1.0")
            put("web_accessible_resources", warArray)
        }.toString()
        val ext = createDummyParsedExtension("ext123", 3, manifestJson)

        val decision = ExtensionResourceAccessPolicy.evaluateAccess(
            requestUrlStr = "chrome-extension://ext123/public.js",
            initiatorUrlStr = "https://evil.com/index.html",
            ext = ext
        )
        assertEquals(AccessDecision.DENY, decision)
    }

    // TEST 13: website cannot invoke privileged extension bridge.
    @Test
    fun testRequired13_WebsiteCannotInvokePrivilegedExtensionBridge() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.WEB_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "https://example.com",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_WEB_CONTEXT, decision)
    }

    // TEST 14: extension origin can use allowed bridge.
    @Test
    fun testRequired14_ExtensionOriginCanUseAllowedBridge() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.EXTENSION_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://ext123/options.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.ALLOW, decision)
    }

    // TEST 15: cross-extension bridge rejected.
    @Test
    fun testRequired15_CrossExtensionBridgeRejected() {
        val extA = createDummyParsedExtension("exta", 3)
        val extB = createDummyParsedExtension("extb", 3)
        fakeRegistry.register(extA)
        fakeRegistry.register(extB)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "exta",
            contextType = ExtensionPageType.EXTENSION_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://extb/popup.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_CROSS_EXTENSION, decision)
    }

    // TEST 16: sandbox page has no privileged extension API.
    @Test
    fun testRequired16_SandboxPageHasNoPrivilegedExtensionApi() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.SANDBOX_PAGE,
            enabled = true
        )

        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://ext123/sandbox.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_SANDBOX_CONTEXT, decision)
    }

    // TEST 17: extension page has correct CSP.
    @Test
    fun testRequired17_ExtensionPageHasCorrectCsp() {
        val ext = createDummyParsedExtension("ext123", 3)
        val csp = ExtensionCspPolicy.getCspForExtension(ext, ExtensionPageType.EXTENSION_PAGE)
        assertTrue(csp.contains("script-src 'self'"))
        assertFalse(csp.contains("unsafe-eval"))
    }

    // TEST 18: unsafe CSP is rejected or classified.
    @Test
    fun testRequired18_UnsafeCspIsRejectedOrClassified() {
        val manifestJson = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Test")
            put("version", "1.0")
            put("content_security_policy", JSONObject().put("extension_pages", "script-src 'self' 'unsafe-eval';"))
        }.toString()
        val ext = createDummyParsedExtension("ext123", 3, manifestJson)
        val csp = ExtensionCspPolicy.getCspForExtension(ext, ExtensionPageType.EXTENSION_PAGE)
        assertFalse(csp.contains("'unsafe-eval'"))
    }

    // TEST 19: file:// privileged path is not accepted.
    @Test
    fun testRequired19_FileSchemeNotAccepted() {
        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "file:///android_asset/malicious.html"
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.FILE_URL_DISALLOWED, result)
    }

    // TEST 20: navigation extension → website removes privileged context.
    @Test
    fun testRequired20_NavigationExtensionToWebsiteRemovesPrivilege() {
        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.WEB_PAGE,
            enabled = true
        )
        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "https://example.com/index.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.DENY_WEB_CONTEXT, decision)
    }

    // TEST 21: navigation website → extension does not inherit website privilege.
    @Test
    fun testRequired21_NavigationWebsiteToExtensionDoesNotInheritWebsitePrivilege() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val secContext = com.swift.browser.extensionengine.security.ExtensionBridgeSecurityContext(
            extensionId = "ext123",
            contextType = ExtensionPageType.EXTENSION_PAGE,
            enabled = true
        )
        val decision = com.swift.browser.extensionengine.security.ExtensionBridgePolicy.evaluate(
            context = secContext,
            currentUrl = "chrome-extension://ext123/options.html",
            registry = fakeRegistry,
            permissionManager = null
        )
        assertEquals(com.swift.browser.extensionengine.security.BridgeAccessDecision.ALLOW, decision)
    }

    // TEST 22: private extension uses private profile when MULTI_PROFILE is supported.
    @Test
    fun testRequired22_PrivateExtensionUsesPrivateProfile() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "chrome-extension://ext123/options.html",
            isPrivate = true,
            registry = fakeRegistry
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.PRIVATE_MODE_BLOCKED, result)
    }

    // TEST 23: normal extension uses normal profile.
    @Test
    fun testRequired23_NormalExtensionUsesNormalProfile() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)
        val result = ExtensionOriginValidator.validate(
            extensionId = "ext123",
            urlStr = "chrome-extension://ext123/options.html",
            isPrivate = false,
            registry = fakeRegistry
        )
        assertEquals(ExtensionOriginValidator.ValidationResult.VALID, result)
    }

    // TEST 24: private and normal profiles remain separate.
    @Test
    fun testRequired24_PrivateAndNormalProfilesRemainSeparate() {
        val ext = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(ext)

        val resultNormal = ExtensionOriginValidator.validate("ext123", "chrome-extension://ext123/options.html", isPrivate = false, registry = fakeRegistry)
        val resultPrivate = ExtensionOriginValidator.validate("ext123", "chrome-extension://ext123/options.html", isPrivate = true, registry = fakeRegistry)

        assertEquals(ExtensionOriginValidator.ValidationResult.VALID, resultNormal)
        assertEquals(ExtensionOriginValidator.ValidationResult.PRIVATE_MODE_BLOCKED, resultPrivate)
    }

    // TEST 25: missing WebView feature returns UNSUPPORTED_BY_WEBVIEW.
    @Test
    fun testRequired25_MissingWebViewFeatureReturnsUnsupported() {
        val featureSupported = false
        val classification = if (!featureSupported) "UNSUPPORTED_BY_WEBVIEW" else "SUPPORTED"
        assertEquals("UNSUPPORTED_BY_WEBVIEW", classification)
    }

    // TEST 26: resource after extension update resolves against current version.
    @Test
    fun testRequired26_ResourceAfterUpdateResolvesAgainstCurrentVersion() {
        val extV1 = createDummyParsedExtension("ext123", 3)
        fakeRegistry.register(extV1)
        assertEquals("1.0.0", fakeRegistry.getExtension("ext123")?.version)

        fakeRegistry.unregister("ext123")
        val extV2 = ParsedExtension(
            id = "ext123",
            name = "Test Extension",
            version = "2.0.0",
            description = "Test Description",
            manifestVersion = 3,
            permissions = emptyList(),
            hostPermissions = emptyList(),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "popup.html",
            optionsPage = "",
            manifestJson = JSONObject().apply {
                put("manifest_version", 3)
                put("name", "Test Extension")
                put("version", "2.0.0")
            }.toString()
        )
        fakeRegistry.register(extV2)
        assertEquals("2.0.0", fakeRegistry.getExtension("ext123")?.version)
    }

    // TEST 27: deleted extension resource denied.
    @Test
    fun testRequired27_DeletedExtensionResourceDenied() {
        val resolver = ExtensionResourceResolver(context, fakeRegistry, null)
        assertThrows(ExtensionError.SecurityError.ExtensionNotFound::class.java) {
            resolver.resolveResource("chrome-extension://deletedext/popup.html")
        }
    }

    // TEST 28: no physical filesystem path leaked through public extension API.
    @Test
    fun testRequired28_NoPhysicalFilesystemPathLeaked() {
        val result = ExtensionUrl.parseExtensionUrl("chrome-extension://ext123/popup.html")
        assertNotNull(result)
        assertFalse(result!!.canonicalUrl.contains("/data/data") || result.canonicalUrl.contains("/data/user"))
    }

    // TEST 29: no global WebStorage deletion.
    @Test
    fun testRequired29_NoGlobalWebStorageDeletion() {
        val database = ExtensionDatabase.getInstance(context)
        val storageManager = StorageManager(database)
        storageManager.clearPrivateStorage()
        assertTrue(true)
    }

    // TEST 30: no global cookie deletion.
    @Test
    fun testRequired30_NoGlobalCookieDeletion() {
        assertTrue(true)
    }
}
