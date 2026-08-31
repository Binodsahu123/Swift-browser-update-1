package com.swift.browser.extensionengine

import org.junit.Assert.*
import org.junit.Test

class ExtensionIdGeneratorTest {

    @Test
    fun testOrionLocalIdentityDeterministicGeneration() {
        val seed = "test_extension_pkg"
        val manifest = """{"name":"Test","version":"1.0"}"""

        val id1 = ExtensionIdGenerator.generateOrionLocalIdentity(seed, manifest)
        val id2 = ExtensionIdGenerator.generateOrionLocalIdentity(seed, manifest)
        val id3 = ExtensionIdGenerator.generateOrionLocalIdentity("other_pkg", manifest)

        assertEquals(32, id1.id.length)
        assertEquals(IdentityType.ORION_LOCAL_IDENTITY, id1.identityType)
        assertEquals(id1.id, id2.id)
        assertNotEquals(id1.id, id3.id)
        assertTrue(id1.id.all { it in 'a'..'p' })
    }

    @Test
    fun testPublicKeyExtensionIdDerivationFormat() {
        // Valid DER RSA public key base64 format simulation
        val pubKeyDERBytes = byteArrayOf(
            0x30, 0x81.toByte(), 0x9f.toByte(), 0x30, 0x0d, 0x06, 0x09, 0x2a.toByte(),
            0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01
        )
        val derivedId = ExtensionPackage.deriveExtensionIdFromPublicKey(pubKeyDERBytes)

        assertEquals(32, derivedId.length)
        assertTrue(derivedId.all { it in 'a'..'p' })
    }
}
