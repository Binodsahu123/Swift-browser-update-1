package com.swift.browser.extensionengine

/**
 * Trust classification for extension packages.
 */
enum class ExtensionTrustState {
    /**
     * Cryptographically signed and verified CRX3 package with verified developer public key.
     */
    TRUSTED_CRX3,

    /**
     * Cryptographically signed and verified legacy CRX2 package.
     */
    TRUSTED_CRX2,

    /**
     * Unsigned local developer package (e.g. sideloaded ZIP for debugging).
     */
    UNSIGNED_LOCAL_DEVELOPER,

    /**
     * Untrusted package whose verification failed or format was rejected.
     */
    UNTRUSTED_REJECTED
}
