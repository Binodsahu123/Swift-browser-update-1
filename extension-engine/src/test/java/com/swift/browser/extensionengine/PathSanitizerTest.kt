package com.swift.browser.extensionengine

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PathSanitizerTest {

    @Test
    fun testValidPathsAllowed() {
        val safePath1 = PathSanitizer.sanitizeRelativePath("background.js")
        val safePath2 = PathSanitizer.sanitizeRelativePath("icons/48.png")
        val safePath3 = PathSanitizer.sanitizeRelativePath("./popup/index.html")

        assertEquals("background.js", safePath1)
        assertEquals("icons/48.png", safePath2)
        assertEquals("popup/index.html", safePath3)
    }

    @Test(expected = ExtensionError.InstallerError.PathTraversalDetected::class)
    fun testPathTraversalWithDotDotRejection() {
        PathSanitizer.sanitizeRelativePath("../etc/passwd")
    }

    @Test(expected = ExtensionError.InstallerError.PathTraversalDetected::class)
    fun testNestedPathTraversalRejection() {
        PathSanitizer.sanitizeRelativePath("images/../../secret.key")
    }

    @Test(expected = ExtensionError.InstallerError.PathTraversalDetected::class)
    fun testUrlEncodedPathTraversalRejection() {
        PathSanitizer.sanitizeRelativePath("%2e%2e%2f%2e%2e%2fetc%2fpasswd")
    }

    @Test(expected = ExtensionError.InstallerError.PathTraversalDetected::class)
    fun testNullByteInjectionRejection() {
        PathSanitizer.sanitizeRelativePath("background.js\u0000.png")
    }

    @Test
    fun testCanonicalContainmentVerification() {
        val rootDir = File("/tmp/ext_root")
        val validChild = File("/tmp/ext_root/sub/file.js")
        val invalidChild = File("/tmp/ext_root/../outside.js")

        assertTrue(PathSanitizer.verifyCanonicalContainment(rootDir, validChild.path))
        assertFalse(PathSanitizer.verifyCanonicalContainment(rootDir, invalidChild.path))
    }
}
