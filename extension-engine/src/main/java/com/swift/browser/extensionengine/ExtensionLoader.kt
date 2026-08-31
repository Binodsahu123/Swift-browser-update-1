package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import java.io.File

class ExtensionLoader(
    private val context: Context,
    private val manifestParser: ManifestParser,
    private val database: ExtensionDatabase
) {

    private val extensionDao = database.extensionDao()
    private val zipInstaller = ZipExtensionInstaller(context, manifestParser)

    /**
     * Unpacks, extracts, parses, stages and indexes a ZIP or CRX extension archive.
     */
    suspend fun loadAndInstallFromZip(uri: Uri): ParsedExtension {
        val parsed = zipInstaller.installFromUri(uri)

        val entity = ExtensionEntity(
            extensionId = parsed.id,
            name = parsed.name,
            shortName = parsed.shortName,
            version = parsed.version,
            description = parsed.description,
            iconPath = parsed.iconPath,
            installPath = parsed.installPath,
            popupPath = parsed.popupPath,
            manifestPath = parsed.manifestPath,
            backgroundPath = parsed.backgroundPath,
            enabledState = true,
            manifestJson = parsed.manifestJson
        )
        extensionDao.insertExtension(entity)
        return parsed
    }

    /**
     * Loads an extension record from the local database.
     */
    suspend fun loadFromDatabase(entity: ExtensionEntity): ParsedExtension {
        val parsed = manifestParser.parse(entity.manifestJson)
        return parsed.copy(
            id = entity.extensionId,
            name = entity.name,
            shortName = entity.shortName,
            version = entity.version,
            description = entity.description,
            iconPath = entity.iconPath,
            installPath = entity.installPath,
            popupPath = entity.popupPath,
            manifestPath = entity.manifestPath,
            backgroundPath = entity.backgroundPath,
            isEnabled = entity.enabledState
        )
    }
}
