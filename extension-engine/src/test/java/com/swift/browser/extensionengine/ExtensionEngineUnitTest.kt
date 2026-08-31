package com.swift.browser.extensionengine

import org.junit.Assert.*
import org.junit.Test

class ExtensionEngineUnitTest {

    @Test
    fun testContentScriptSpecMatching() {
        val spec = ContentScriptSpec(
            matches = listOf("https://*.example.com/*"),
            js = listOf("inject.js"),
            css = emptyList()
        )

        assertEquals(1, spec.matches.size)
        assertEquals("inject.js", spec.js.first())
        assertEquals("document_idle", spec.runAt)
    }

    @Test
    fun testPathSafetyAndIdGeneration() {
        assertTrue(NativeExtensionEngine.isSafeRelativePath("scripts/content.js"))
        assertTrue(NativeExtensionEngine.isSafeRelativePath("assets/icons/icon_48.png"))
        assertFalse(NativeExtensionEngine.isSafeRelativePath("../etc/passwd"))
        assertFalse(NativeExtensionEngine.isSafeRelativePath("scripts/../../secret.txt"))

        val id1 = NativeExtensionEngine.generateExtensionId("MyExtension")
        val id2 = NativeExtensionEngine.generateExtensionId("MyExtension")
        val id3 = NativeExtensionEngine.generateExtensionId("OtherExtension")

        assertEquals(32, id1.length)
        assertEquals(id1, id2)
        assertNotEquals(id1, id3)
    }
}
