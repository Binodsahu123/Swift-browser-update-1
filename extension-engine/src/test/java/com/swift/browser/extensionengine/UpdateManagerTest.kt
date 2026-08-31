package com.swift.browser.extensionengine

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateManagerTest {

    @Test
    fun testUpdateManifestParsingXml() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gupdate xmlns="http://www.google.com/update2/response" protocol="2.0">
              <app appid="abcdefghijklmnopabcdefghijklmnop">
                <updatecheck codebase="https://example.com/extension_1_0_1.crx" version="1.0.1" />
              </app>
            </gupdate>
        """.trimIndent()

        val parser = UpdateManifestParser()
        val updates = parser.parseUpdateManifest(xml)

        assertEquals(1, updates.size)
        assertEquals("abcdefghijklmnopabcdefghijklmnop", updates[0].extensionId)
        assertEquals("1.0.1", updates[0].version)
        assertEquals("https://example.com/extension_1_0_1.crx", updates[0].codebaseUrl)
    }

    @Test
    fun testUpdateManifestParsingJson() {
        val json = """
            {
                "apps": [
                    {
                        "appid": "myextid123456789012345678901234",
                        "updatecheck": {
                            "version": "2.0.0",
                            "codebase": "https://example.com/myext_2.crx"
                        }
                    }
                ]
            }
        """.trimIndent()

        val parser = UpdateManifestParser()
        val updates = parser.parseUpdateManifest(json)

        assertEquals(1, updates.size)
        assertEquals("myextid123456789012345678901234", updates[0].extensionId)
        assertEquals("2.0.0", updates[0].version)
        assertEquals("https://example.com/myext_2.crx", updates[0].codebaseUrl)
    }
}
