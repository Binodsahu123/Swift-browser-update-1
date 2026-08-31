package com.swift.browser.extensionengine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AtomicExtensionInstallerTest {

    private fun createValidZipBytes(manifestJson: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("background.js"))
            zos.write("console.log('bg');".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @Test
    fun testVersionComparison() {
        assertTrue(ZipExtensionInstaller.compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(ZipExtensionInstaller.compareVersions("2.0.0", "1.9.9.9") > 0)
        assertEquals(0, ZipExtensionInstaller.compareVersions("1.2.3", "1.2.3"))
        assertTrue(ZipExtensionInstaller.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun testEmptyZipRejection() {
        val emptyZip = ByteArray(0)
        try {
            ExtensionPackage.parseAndValidate(emptyZip)
            fail("Expected exception for empty payload")
        } catch (e: Exception) {
            // Success
        }
    }
}
