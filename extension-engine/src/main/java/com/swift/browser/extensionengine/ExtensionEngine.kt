package com.swift.browser.extensionengine

import android.net.Uri

/**
 * Interface representing the extension integration subsystem.
 * Handles loading, unloading, registering triggers, service worker lifecycles,
 * popup managers, and messaging protocols.
 */
interface ExtensionEngine {
    /**
     * Installs, registers, and initiates runtime routines for an extension zip.
     */
    suspend fun installExtension(uri: Uri): ParsedExtension

    /**
     * Unregisters and deletes extension metadata and background structures from storage.
     */
    suspend fun uninstallExtension(id: String)

    /**
     * Toggles the active running state of an extension.
     */
    suspend fun toggleExtension(id: String, enabled: Boolean)

    /**
     * Shuts down all active service workers, background frames, and ports.
     */
    fun shutdown()

    /**
     * Sets whether an extension is allowed to run in private tabs.
     */
    fun setAllowedInPrivate(id: String, allowed: Boolean)

    /**
     * Checks if an extension is allowed to run in private tabs.
     */
    fun isAllowedInPrivate(id: String): Boolean

    @Deprecated("Use setAllowedInPrivate instead", ReplaceWith("setAllowedInPrivate(id, allowed)"))
    fun setAllowedInIncognito(id: String, allowed: Boolean) = setAllowedInPrivate(id, allowed)

    @Deprecated("Use isAllowedInPrivate instead", ReplaceWith("isAllowedInPrivate(id)"))
    fun isAllowedInIncognito(id: String): Boolean = isAllowedInPrivate(id)
}
