package com.swift.browser.extensionengine

import java.io.File

object PathSanitizer {

    /**
     * Sanitizes and normalizes an entry path from an archive or URL request.
     * Rejects path traversal attempts, absolute paths, null bytes, and unsafe separators.
     * Throws [ExtensionError.InstallerError.PathTraversalDetected] if unsafe.
     */
    fun sanitizeRelativePath(relativePath: String): String {
        if (relativePath.contains("\u0000")) {
            throw ExtensionError.InstallerError.PathTraversalDetected(relativePath)
        }

        // Iterative URL decoding to uncover hidden encoded traversal sequences (e.g. %2e%2e, %2f, %00)
        var decoded = relativePath
        try {
            var prev = ""
            var iterations = 0
            while (decoded != prev && decoded.contains("%") && iterations < 3) {
                prev = decoded
                decoded = java.net.URLDecoder.decode(decoded, "UTF-8")
                iterations++
            }
        } catch (e: Exception) {
            throw ExtensionError.InstallerError.PathTraversalDetected(relativePath)
        }

        if (decoded.contains("\u0000")) {
            throw ExtensionError.InstallerError.PathTraversalDetected(relativePath)
        }

        var normalized = decoded.replace('\\', '/').trim()
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim()
        }
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2).trim()
        }

        if (normalized.matches(Regex("^[a-zA-Z]:.*"))) {
            throw ExtensionError.InstallerError.PathTraversalDetected(relativePath)
        }

        val segments = normalized.split('/')
        for (segment in segments) {
            if (segment == ".." || segment.contains("\u0000")) {
                throw ExtensionError.InstallerError.PathTraversalDetected(relativePath)
            }
        }

        return normalized
    }

    /**
     * Verifies that targetFile is strictly contained within rootDirectory using canonical path evaluation.
     */
    fun verifyCanonicalContainment(rootDirectory: File, targetFile: File) {
        val rootCanonical = rootDirectory.canonicalPath
        val targetCanonical = targetFile.canonicalPath

        if (!targetCanonical.startsWith(rootCanonical + File.separator) && targetCanonical != rootCanonical) {
            throw ExtensionError.InstallerError.PathTraversalDetected(targetFile.path)
        }
    }

    /**
     * Verifies canonical containment returning a Boolean decision without throwing.
     */
    fun verifyCanonicalContainment(rootDirectory: File, targetPath: String): Boolean {
        return try {
            val rootCanonical = rootDirectory.canonicalPath
            val targetFile = File(targetPath)
            val targetCanonical = targetFile.canonicalPath
            targetCanonical.startsWith(rootCanonical + File.separator) || targetCanonical == rootCanonical
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if relativePath is safe without throwing exceptions.
     */
    fun isSafePath(relativePath: String): Boolean {
        return try {
            sanitizeRelativePath(relativePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isSafeRelativePath(relativePath: String): Boolean {
        return isSafePath(relativePath)
    }

    /**
     * Validates that an extension ID consists strictly of alphanumeric characters, hyphens, and underscores.
     */
    fun isSafeExtensionId(extensionId: String): Boolean {
        if (extensionId.isBlank() || extensionId.length > 64) return false
        return extensionId.matches(Regex("^[a-zA-Z0-9_-]+$"))
    }
}
