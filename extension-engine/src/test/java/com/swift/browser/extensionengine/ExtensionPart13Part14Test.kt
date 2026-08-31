package com.swift.browser.extensionengine

import android.content.Context
import com.swift.browser.nativeextensionruntime.NativeExtensionRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ExtensionPart13Part14Test {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var registry: ExtensionRegistry
    private lateinit var installer: ZipExtensionInstaller
    private lateinit var updateManager: UpdateManager

    // Sample Base64 RSA public key for deterministic extension ID derivation
    private val TEST_PUB_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCl8X5Z3Y1w8kP3v6E5j9xQ0f1g2h3i4j5k6l7m8n9o"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        permissionManager = PermissionManager(context)
        registry = ExtensionRegistry()
        permissionManager.setRegistry(registry)
        installer = ZipExtensionInstaller(context, registry = registry)
        updateManager = UpdateManager(context, installer, registry)
    }

    @Test
    fun testPermissionEngineDenyByDefault() {
        // SwiftExtensionPermissionEngine MUST return false by default for unregistered host permissions
        val hostGranted = SwiftExtensionPermissionEngine.isHostPermissionGranted(
            context = context,
            extId = "test_ext_1",
            url = "https://evil.com/page"
        )
        assertFalse("Unregistered or non-matching host permission must be denied", hostGranted)

        val apiGranted = NativeExtensionRuntime.validatePermission(
            allowedPermissions = listOf("tabs"),
            requiredPermission = "cookies"
        )
        assertFalse("Unallowed API permission must be denied", apiGranted)
    }

    @Test
    fun testCrx2Crx3HeaderParsing() {
        val invalidBytes = byteArrayOf(0x43, 0x72, 0x32, 0x34, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val parsedInvalid = ExtensionPackage.parseAndValidate(invalidBytes, "corrupt_crx")
        assertEquals("Corrupt CRX header validation result", HeaderValidationResult.HEADER_INVALID, parsedInvalid.headerValidation)
        assertFalse("Corrupt signature must fail verification", parsedInvalid.isSignatureVerified)

        val plainZipBytes = createTestZip("1.0.0")
        val parsedZip = ExtensionPackage.parseAndValidate(plainZipBytes, "plain_zip")
        assertEquals("Plain zip header validation", HeaderValidationResult.PLAIN_ZIP, parsedZip.headerValidation)
        assertEquals("Plain zip signature result", SignatureVerificationResult.UNSIGNED, parsedZip.signatureVerification)
    }

    @Test
    fun testInstallAndDowngradeRejection() {
        val zipV1 = createTestZip("1.0.0", key = TEST_PUB_KEY)
        val extV1 = installer.installFromBytes(zipV1, "ext_v1")
        assertNotNull("Extension v1 installed successfully", extV1)
        assertEquals("1.0.0", extV1.version)

        registry.register(extV1)

        val zipV1Copy = createTestZip("1.0.0", key = TEST_PUB_KEY)
        var rejectionOccurred = false
        try {
            installer.installFromBytes(zipV1Copy, "ext_v1_copy")
        } catch (e: ExtensionError.InstallerError.InstallationRejected) {
            rejectionOccurred = true
            assertTrue("Rejection contains INSTALLATION_REJECTED", e.message!!.contains("INSTALLATION_REJECTED"))
        } catch (e: Exception) {
            rejectionOccurred = e.message?.contains("INSTALLATION_REJECTED") == true
        }
        assertTrue("Duplicate or lower version update must be rejected", rejectionOccurred)
    }

    @Test
    fun testUpdateSuccessAndRollbackOnFailure() {
        val zipV1 = createTestZip("1.0.0", key = TEST_PUB_KEY)
        val extV1 = installer.installFromBytes(zipV1, "test_update_ext")
        registry.register(extV1)

        val zipV2 = createTestZip("2.0.0", key = TEST_PUB_KEY)
        val extV2 = updateManager.updateExtension(extV1.id, zipV2)
        assertEquals("2.0.0", extV2.version)
        assertEquals("2.0.0", registry.getExtension(extV1.id)?.version)

        // Try updating with corrupted archive -> rollback to v2
        var updateFailed = false
        try {
            updateManager.updateExtension(extV1.id, byteArrayOf(0x00, 0x01, 0x02))
        } catch (e: Exception) {
            updateFailed = true
        }
        assertTrue("Corrupted update payload must fail", updateFailed)
        assertEquals("Extension state remains version 2.0.0 after rollback", "2.0.0", registry.getExtension(extV1.id)?.version)
    }

    @Test
    fun testExtensionSenderCrossApiContext() {
        val sender = ExtensionSender(
            extensionId = "test_sender_ext",
            tabId = "tab_123",
            windowId = "1",
            frameId = 0,
            origin = "chrome-extension://test_sender_ext",
            isPrivate = true,
            privateSessionId = "priv_session_456"
        )

        val json = sender.toJSONObject()
        assertEquals("test_sender_ext", json.getString("id"))
        assertTrue(json.getBoolean("isPrivate"))
        assertEquals("priv_session_456", json.getString("privateSessionId"))
        assertNotNull(json.optJSONObject("tab"))
    }

    private fun createTestZip(version: String, key: String? = null): ByteArray {
        val keyJsonField = if (key != null) "\"key\": \"$key\"," else ""
        val manifestJson = """
            {
              "manifest_version": 3,
              "name": "Test Extension",
              "version": "$version",
              $keyJsonField
              "description": "Test extension description",
              "permissions": ["tabs", "storage"]
            }
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("manifest.json")
            zos.putNextEntry(entry)
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
