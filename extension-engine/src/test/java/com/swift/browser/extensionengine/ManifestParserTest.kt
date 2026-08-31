package com.swift.browser.extensionengine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManifestParserTest {

    private lateinit var parser: ManifestParser

    @Before
    fun setUp() {
        parser = ManifestParser()
    }

    @Test
    fun testValidMv3ManifestParsing() {
        val json = """
            {
                "manifest_version": 3,
                "name": "Test MV3 Extension",
                "version": "1.0.0",
                "description": "A test extension for MV3",
                "permissions": ["storage", "activeTab"],
                "host_permissions": ["https://*.example.com/*"],
                "action": {
                    "default_popup": "popup.html",
                    "default_title": "Test Popup"
                },
                "background": {
                    "service_worker": "background.js"
                }
            }
        """.trimIndent()

        val parsed = parser.parse(json)

        assertEquals(3, parsed.manifestVersion)
        assertEquals("Test MV3 Extension", parsed.name)
        assertEquals("1.0.0", parsed.version)
        assertTrue(parsed.isServiceWorker)
        assertEquals("background.js", parsed.backgroundPath)
        assertEquals("popup.html", parsed.popupPath)
        assertTrue(parsed.permissions.contains("storage"))
        assertTrue(parsed.hostPermissions.contains("https://*.example.com/*"))
    }

    @Test
    fun testValidMv2ManifestParsing() {
        val json = """
            {
                "manifest_version": 2,
                "name": "Test MV2 Extension",
                "version": "2.1.4",
                "permissions": ["tabs", "<all_urls>"],
                "browser_action": {
                    "default_popup": "index.html"
                },
                "background": {
                    "scripts": ["bg.js"]
                }
            }
        """.trimIndent()

        val parsed = parser.parse(json)

        assertEquals(2, parsed.manifestVersion)
        assertEquals("Test MV2 Extension", parsed.name)
        assertFalse(parsed.isServiceWorker)
        assertEquals("bg.js", parsed.backgroundPath)
        assertEquals("index.html", parsed.popupPath)
    }

    @Test(expected = ExtensionError.ManifestError.MissingRequiredField::class)
    fun testMissingNameRejection() {
        val json = """
            {
                "manifest_version": 3,
                "version": "1.0.0"
            }
        """.trimIndent()

        parser.parse(json)
    }

    @Test(expected = ExtensionError.ManifestError.UnsupportedVersion::class)
    fun testUnsupportedManifestVersionRejection() {
        val json = """
            {
                "manifest_version": 1,
                "name": "Legacy App",
                "version": "1.0"
            }
        """.trimIndent()

        parser.parse(json)
    }

    @Test(expected = ExtensionError.ManifestError.InvalidVersionFormat::class)
    fun testInvalidSemverVersionRejection() {
        val json = """
            {
                "manifest_version": 3,
                "name": "Bad Version App",
                "version": "invalid_version_str"
            }
        """.trimIndent()

        parser.parse(json)
    }
}
