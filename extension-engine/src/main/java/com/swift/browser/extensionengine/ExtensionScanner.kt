package com.swift.browser.extensionengine

import java.io.File

class ExtensionScanner {

    /**
     * Scans a target directory for extension packages (.crx, .zip) or unpacked extension directories containing a manifest.json.
     */
    fun scanDirectory(directory: File): List<ExtensionPackage> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val results = mutableListOf<ExtensionPackage>()
        val files = directory.listFiles() ?: return emptyList()

        for (file in files) {
            try {
                if (file.isDirectory) {
                    val manifestFile = File(file, "manifest.json")
                    if (manifestFile.exists() && manifestFile.isFile) {
                        val bytes = manifestFile.readBytes()
                        val pkg = ExtensionPackage(
                            rawBytes = bytes,
                            sourceName = file.name,
                            headerValidation = HeaderValidationResult.PLAIN_ZIP,
                            signatureVerification = SignatureVerificationResult.UNSIGNED,
                            zipPayloadBytes = bytes,
                            rootPath = file.absolutePath,
                            installationState = PackageInstallationState.DISCOVERED
                        )
                        results.add(pkg)
                    }
                } else if (file.isFile && (file.name.endsWith(".crx", ignoreCase = true) || file.name.endsWith(".zip", ignoreCase = true))) {
                    val bytes = file.readBytes()
                    val pkg = ExtensionPackage.parseAndValidate(bytes, file.name)
                    val fullPkg = pkg.copy(
                        archivePath = file.absolutePath,
                        installationState = PackageInstallationState.DISCOVERED
                    )
                    results.add(fullPkg)
                }
            } catch (e: Exception) {
                // Ignore corrupt individual items during scanning
            }
        }
        return results
    }
}
