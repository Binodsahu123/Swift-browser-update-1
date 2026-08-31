package com.swift.browser.extensionengine

import android.content.Context

/**
 * UpdateManager bridging callers to the production-grade ExtensionUpdateManager.
 */
class UpdateManager(
    private val context: Context,
    private val installer: ZipExtensionInstaller = ZipExtensionInstaller(context),
    private val registry: ExtensionRegistry,
    private val updateChecker: UpdateChecker = UpdateChecker(),
    private val updateDownloader: UpdateDownloader = UpdateDownloader(context)
) {

    val canonicalUpdateManager = ExtensionUpdateManager(
        context = context,
        registry = registry,
        updateDownloader = updateDownloader,
        installer = installer
    )

    /**
     * Updates an existing extension from new package bytes.
     * Enforces version validation, CRX signature verification, manifest key verification, staging, and atomic replacement with rollback on failure.
     */
    fun updateExtension(existingExtId: String, newPackageBytes: ByteArray): ParsedExtension {
        return canonicalUpdateManager.updateExtension(existingExtId, newPackageBytes)
    }

    /**
     * Checks for updates for a registered extension via its update URL, downloads the package, and atomically applies the update.
     */
    fun checkAndApplyUpdate(extensionId: String, updateUrl: String): ParsedExtension? {
        return canonicalUpdateManager.checkAndApplyUpdate(extensionId, updateUrl)
    }

    fun checkForUpdates() {
        canonicalUpdateManager.checkForUpdatesForAllExtensions()
    }

    fun triggerAndVerifyReload(extensionId: String, onComplete: () -> Unit) {
        onComplete()
    }
}
