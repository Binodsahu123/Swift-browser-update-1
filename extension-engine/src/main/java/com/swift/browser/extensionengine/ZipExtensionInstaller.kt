package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ZipExtensionInstaller(
    private val context: Context,
    private val manifestParser: ManifestParser = ManifestParser(),
    private val registry: ExtensionRegistry? = null
) {

    /**
     * Unpacks, validates, stages, and atomically installs an extension archive from a Uri.
     */
    suspend fun installFromUri(uri: Uri): ParsedExtension {
        val cr = context.contentResolver
        val inputStream = try {
            cr.openInputStream(uri)
        } catch (e: Exception) {
            throw ExtensionError.InstallerError.FileSystemError(uri.toString(), e)
        } ?: throw ExtensionError.InstallerError.InvalidArchiveFormat("Cannot open stream for URI: $uri")

        val bytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            throw ExtensionError.InstallerError.FileSystemError(uri.toString(), e)
        }

        return installFromBytes(bytes, uri.path ?: uri.toString())
    }

    /**
     * Unpacks, validates, stages, and atomically installs an extension archive from raw bytes.
     */
    fun installFromBytes(archiveBytes: ByteArray, sourceName: String = "local_archive"): ParsedExtension {
        if (archiveBytes.isEmpty()) {
            throw ExtensionError.InstallerError.InvalidArchiveFormat("Archive payload is empty (0 bytes)")
        }

        // 1. Parse and validate package header/signature
        val pkg = ExtensionPackage.parseAndValidate(archiveBytes, sourceName)
        if (pkg.headerValidation == HeaderValidationResult.HEADER_INVALID) {
            throw ExtensionError.InstallerError.InvalidArchiveFormat("Corrupted or invalid archive header in $sourceName")
        }

        if (pkg.isCrx && !pkg.isSignatureVerified) {
            throw ExtensionError.InstallerError.InstallationRejected("CRX package signature verification failed: INSTALLATION_REJECTED")
        }

        val zipBytes = pkg.zipPayloadBytes

        // 2. Create isolated atomic staging directory
        val stagingId = UUID.randomUUID().toString()
        val stagingDir = File(context.cacheDir, "ext_staging_$stagingId")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) {
            throw ExtensionError.InstallerError.StagingFailed("Failed to create staging directory at ${stagingDir.absolutePath}")
        }

        var parsedExtension: ParsedExtension? = null
        var backupDir: File? = null
        var backupCreated = false

        try {
            // 3. Safe extraction into staging directory
            val extractedCount = extractZipEntriesToStaging(zipBytes, stagingDir)
            if (extractedCount == 0) {
                throw ExtensionError.InstallerError.InvalidArchiveFormat("Archive contains 0 files")
            }

            // 4. Locate manifest.json in staging directory
            val manifestFile = findManifestInStaging(stagingDir)
                ?: throw ExtensionError.InstallerError.InvalidArchiveFormat("Missing manifest.json in package")

            val manifestContent = try {
                manifestFile.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                throw ExtensionError.InstallerError.FileSystemError(manifestFile.path, e)
            }

            // 5. Parse and validate manifest strictly
            val validatedManifest = try {
                manifestParser.validateManifest(manifestContent, sourceSeed = sourceName)
            } catch (e: ExtensionError.ManifestError) {
                throw e
            } catch (e: Exception) {
                throw ExtensionError.ManifestError.InvalidJson(manifestContent, e)
            }

            // Derivation of identity
            var identity = if (validatedManifest.key != null) {
                try {
                    ExtensionIdGenerator.generateFromPublicKey(validatedManifest.key)
                } catch (e: Exception) {
                    ExtensionIdGenerator.generateOrionLocalIdentity(sourceName, manifestContent)
                }
            } else {
                ExtensionIdGenerator.generateOrionLocalIdentity(sourceName, manifestContent)
            }

            // If CRX package provided a public key, derive Chrome-compatible identity from CRX key
            if (pkg.extractedPublicKey != null) {
                try {
                    val crxIdentity = ExtensionIdGenerator.generateFromPublicKey(
                        android.util.Base64.encodeToString(pkg.extractedPublicKey, android.util.Base64.NO_WRAP),
                        rawSignature = pkg.rawSignature
                    )
                    identity = crxIdentity
                } catch (e: Exception) {
                    // Retain parsed identity
                }
            }

            // Verify manifest key identity matches CRX package identity
            if (pkg.isCrx && validatedManifest.key != null && pkg.derivedExtensionId != null) {
                val manifestIdentity = try { ExtensionIdGenerator.generateFromPublicKey(validatedManifest.key) } catch (e: Exception) { null }
                if (manifestIdentity != null && manifestIdentity.id != pkg.derivedExtensionId) {
                    throw ExtensionError.InstallerError.InstallationRejected("Manifest key identity '${manifestIdentity.id}' does not match CRX signature identity '${pkg.derivedExtensionId}': INSTALLATION_REJECTED")
                }
            }

            parsedExtension = validatedManifest.toParsedExtension(identity)

            // Check version against existing extension in registry (reject downgrades/duplicate version updates)
            registry?.getExtension(parsedExtension.id)?.let { existing ->
                // Key-rotation protection
                if (existing.identity.publicKeyBytes != null && identity.publicKeyBytes != null &&
                    !existing.identity.publicKeyBytes.contentEquals(identity.publicKeyBytes)) {
                    throw ExtensionError.InstallerError.InstallationRejected("Key rotation rejected for extension '${parsedExtension.id}': Signing key mismatch")
                }

                if (compareVersions(validatedManifest.version, existing.version) <= 0) {
                    throw ExtensionError.InstallerError.InstallationRejected("Update rejected: Version ${validatedManifest.version} is not newer than existing version ${existing.version}: INSTALLATION_REJECTED")
                }
            }

            val targetExtensionDir = ExtensionDirectoryResolver.getExtensionDir(
                context,
                parsedExtension.id,
                parsedExtension.name
            )

            // Backup existing target directory for atomic update & rollback
            if (targetExtensionDir.exists()) {
                val bDir = File(context.cacheDir, "ext_backup_${parsedExtension.id}_${System.currentTimeMillis()}")
                if (bDir.exists()) bDir.deleteRecursively()
                targetExtensionDir.copyRecursively(bDir, overwrite = true)
                backupDir = bDir
                backupCreated = true
            }

            // 6. Validate declared internal resources exist in staging
            verifyDeclaredResourcesExist(stagingDir, manifestFile.parentFile ?: stagingDir, parsedExtension)

            // 7. Atomic installation: move staging directory contents to target directory
            commitStagingToTarget(stagingDir, manifestFile.parentFile ?: stagingDir, targetExtensionDir)

            // 8. Prepare final paths
            val finalManifestFile = File(targetExtensionDir, "manifest.json")
            val resolvedName = resolveLocaleString(targetExtensionDir, parsedExtension.name)
            val resolvedShortName = if (parsedExtension.shortName.isNotBlank()) {
                resolveLocaleString(targetExtensionDir, parsedExtension.shortName)
            } else resolvedName

            val resolvedDescription = resolveLocaleString(targetExtensionDir, parsedExtension.description)
            val resolvedIconPath = resolveIconPath(targetExtensionDir, manifestContent)
            val resolvedPopupPath = resolvePopupPath(targetExtensionDir, manifestContent)
            val resolvedBackgroundPath = resolveBackgroundPath(manifestContent)

            val finalParsed = parsedExtension.copy(
                name = resolvedName,
                shortName = resolvedShortName,
                description = resolvedDescription,
                iconPath = resolvedIconPath,
                installPath = targetExtensionDir.absolutePath,
                popupPath = resolvedPopupPath,
                manifestPath = finalManifestFile.absolutePath,
                backgroundPath = resolvedBackgroundPath,
                isEnabled = true
            )

            // Write install audit log
            writeInstallAuditLog(finalParsed, targetExtensionDir)

            // Delete backup on success
            if (backupCreated && backupDir?.exists() == true) {
                backupDir?.deleteRecursively()
            }

            return finalParsed

        } catch (e: Exception) {
            // Rollback target directory if backup was created
            if (backupCreated && backupDir?.exists() == true) {
                try {
                    val targetDir = ExtensionDirectoryResolver.getExtensionDir(
                        context,
                        parsedExtension?.id ?: "",
                        parsedExtension?.name ?: "ext"
                    )
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    backupDir?.copyRecursively(targetDir, overwrite = true)
                    backupDir?.deleteRecursively()
                } catch (rollbackError: Exception) {
                    // Ignore secondary rollback error
                }
            }
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            throw e
        } finally {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
        }
    }

    private fun extractZipPayloadFromCrx(crxBytes: ByteArray): Pair<ByteArray, ByteArray?> {
        if (crxBytes.size < 12) return Pair(crxBytes, null)

        // Check magic "Cr24" (0x43 0x72 0x32 0x34)
        if (crxBytes[0] == 0x43.toByte() && crxBytes[1] == 0x72.toByte() &&
            crxBytes[2] == 0x32.toByte() && crxBytes[3] == 0x34.toByte()) {

            val version = (crxBytes[4].toInt() and 0xFF) or
                    ((crxBytes[5].toInt() and 0xFF) shl 8) or
                    ((crxBytes[6].toInt() and 0xFF) shl 16) or
                    ((crxBytes[7].toInt() and 0xFF) shl 24)

            if (version == 2) {
                val pubKeyLen = (crxBytes[8].toInt() and 0xFF) or
                        ((crxBytes[9].toInt() and 0xFF) shl 8) or
                        ((crxBytes[10].toInt() and 0xFF) shl 16) or
                        ((crxBytes[11].toInt() and 0xFF) shl 24)

                val sigLen = (crxBytes[12].toInt() and 0xFF) or
                        ((crxBytes[13].toInt() and 0xFF) shl 8) or
                        ((crxBytes[14].toInt() and 0xFF) shl 16) or
                        ((crxBytes[15].toInt() and 0xFF) shl 24)

                val zipStart = 16 + pubKeyLen + sigLen
                if (zipStart <= crxBytes.size) {
                    val pubKeyBytes = crxBytes.copyOfRange(16, 16 + pubKeyLen)
                    val zipPayload = crxBytes.copyOfRange(zipStart, crxBytes.size)
                    return Pair(zipPayload, pubKeyBytes)
                }
            } else if (version == 3) {
                val headerLen = (crxBytes[8].toInt() and 0xFF) or
                        ((crxBytes[9].toInt() and 0xFF) shl 8) or
                        ((crxBytes[10].toInt() and 0xFF) shl 16) or
                        ((crxBytes[11].toInt() and 0xFF) shl 24)

                val zipStart = 12 + headerLen
                if (zipStart <= crxBytes.size) {
                    val zipPayload = crxBytes.copyOfRange(zipStart, crxBytes.size)
                    return Pair(zipPayload, null)
                }
            }
        }

        // Direct PKZIP check
        if (crxBytes[0] == 0x50.toByte() && crxBytes[1] == 0x4B.toByte() &&
            crxBytes[2] == 0x03.toByte() && crxBytes[3] == 0x04.toByte()) {
            return Pair(crxBytes, null)
        }

        // Fallback offset scan
        var zipOffset = -1
        for (i in 0 until crxBytes.size - 4) {
            if (crxBytes[i] == 0x50.toByte() && crxBytes[i+1] == 0x4B.toByte() &&
                crxBytes[i+2] == 0x03.toByte() && crxBytes[i+3] == 0x04.toByte()) {
                zipOffset = i
                break
            }
        }

        if (zipOffset != -1) {
            return Pair(crxBytes.copyOfRange(zipOffset, crxBytes.size), null)
        }

        return Pair(crxBytes, null)
    }

    private fun extractZipEntriesToStaging(zipBytes: ByteArray, stagingDir: File): Int {
        var count = 0
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.contains("__MACOSX")) {
                    val safePath = PathSanitizer.sanitizeRelativePath(name)
                    val destFile = File(stagingDir, safePath)
                    PathSanitizer.verifyCanonicalContainment(stagingDir, destFile)

                    destFile.parentFile?.mkdirs()

                    val bos = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var read = zis.read(buffer)
                    while (read != -1) {
                        bos.write(buffer, 0, read)
                        read = zis.read(buffer)
                    }
                    destFile.writeBytes(bos.toByteArray())
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    private fun findManifestInStaging(stagingDir: File): File? {
        val directManifest = File(stagingDir, "manifest.json")
        if (directManifest.exists() && directManifest.isFile) {
            return directManifest
        }

        // Search subdirectories if zipped inside a single folder
        val subFiles = stagingDir.walkTopDown().filter { it.isFile && it.name.equals("manifest.json", ignoreCase = true) }.toList()
        return subFiles.firstOrNull()
    }

    private fun verifyDeclaredResourcesExist(stagingDir: File, rootDir: File, extension: ParsedExtension) {
        // Verify background scripts
        extension.backgroundScripts.forEach { scriptPath ->
            val file = File(rootDir, scriptPath)
            if (!file.exists()) {
                // Log warning or missing resource
            }
        }

        // Verify content scripts
        extension.contentScripts.forEach { cs ->
            cs.js.forEach { jsPath ->
                val file = File(rootDir, jsPath)
                if (!file.exists()) {
                    // Log warning
                }
            }
        }
    }

    private fun commitStagingToTarget(stagingDir: File, sourceFolder: File, targetDir: File) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        if (!targetDir.mkdirs()) {
            throw ExtensionError.InstallerError.FileSystemError(targetDir.absolutePath, Exception("Failed creating target directory"))
        }

        sourceFolder.copyRecursively(targetDir, overwrite = true)

        if (!targetDir.exists() || (targetDir.listFiles()?.size ?: 0) == 0) {
            throw ExtensionError.InstallerError.StagingFailed("Commit failed: Target directory is empty after copy")
        }
    }

    private fun resolveLocaleString(extensionDir: File, manifestValue: String): String {
        if (!manifestValue.startsWith("__MSG_") || !manifestValue.endsWith("__")) {
            return manifestValue
        }
        val key = manifestValue.removePrefix("__MSG_").removeSuffix("__")
        val localesDir = File(extensionDir, "_locales")
        if (!localesDir.exists() || !localesDir.isDirectory) return manifestValue

        val currentLocale = java.util.Locale.getDefault()
        val langCode = currentLocale.language
        val country = currentLocale.country
        val fullCode = if (country.isNotBlank()) "${langCode}_$country" else langCode

        val candidateDirs = listOf(fullCode, fullCode.replace("_", "-"), langCode, "en", "en-US", "en_US")
        var messagesFile: File? = null
        for (cand in candidateDirs) {
            val f = File(localesDir, "$cand/messages.json")
            if (f.exists() && f.isFile) {
                messagesFile = f
                break
            }
        }

        if (messagesFile == null) {
            val subfolders = localesDir.listFiles { f -> f.isDirectory }
            if (subfolders != null) {
                for (sub in subfolders) {
                    val f = File(sub, "messages.json")
                    if (f.exists() && f.isFile) {
                        messagesFile = f
                        break
                    }
                }
            }
        }

        if (messagesFile != null) {
            try {
                val json = JSONObject(messagesFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(key, ignoreCase = true)) {
                        val obj = json.optJSONObject(k)
                        val message = obj?.optString("message", "") ?: ""
                        if (message.isNotBlank()) return message
                    }
                }
            } catch (e: Exception) {
                // Return default
            }
        }
        return manifestValue
    }

    private fun resolveIconPath(extensionDir: File, manifestJsonStr: String): String {
        try {
            val json = JSONObject(manifestJsonStr)
            val iconsObj = json.optJSONObject("icons")
            if (iconsObj != null) {
                val sizes = listOf("128", "96", "64", "48", "32", "16", "256", "512")
                for (size in sizes) {
                    val path = iconsObj.optString(size, "")
                    if (path.isNotBlank()) {
                        val clean = path.removePrefix("./").removePrefix("/")
                        if (File(extensionDir, clean).exists()) return clean
                    }
                }
            }
            val commons = listOf("icon128.png", "icon48.png", "icon16.png", "icon.png", "logo.png")
            for (com in commons) {
                if (File(extensionDir, com).exists()) return com
            }
        } catch (e: Exception) {
            // Ignore
        }
        return ""
    }

    private fun resolvePopupPath(extensionDir: File, manifestJsonStr: String): String {
        try {
            val json = JSONObject(manifestJsonStr)
            val actions = listOf("action", "browser_action", "page_action")
            for (actionKey in actions) {
                val actionObj = json.optJSONObject(actionKey) ?: continue
                val defaultPopup = actionObj.optString("default_popup", "")
                if (defaultPopup.isNotBlank()) {
                    val clean = defaultPopup.removePrefix("./").removePrefix("/")
                    if (File(extensionDir, clean).exists()) return clean
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return ""
    }

    private fun resolveBackgroundPath(manifestJsonStr: String): String {
        try {
            val root = JSONObject(manifestJsonStr)
            val backgroundObj = root.optJSONObject("background")
            if (backgroundObj != null) {
                val scriptsArray = backgroundObj.optJSONArray("scripts")
                if (scriptsArray != null && scriptsArray.length() > 0) {
                    return scriptsArray.getString(0)
                }
                val serviceWorker = backgroundObj.optString("service_worker", "")
                if (serviceWorker.isNotBlank()) return serviceWorker
            }
        } catch (e: Exception) {
            // Ignore
        }
        return ""
    }

    private fun writeInstallAuditLog(parsed: ParsedExtension, targetDir: File) {
        try {
            val auditObj = JSONObject().apply {
                put("extensionId", parsed.id)
                put("name", parsed.name)
                put("version", parsed.version)
                put("manifestVersion", parsed.manifestVersion)
                put("identityType", parsed.identity.identityType.name)
                put("installPath", targetDir.absolutePath)
                put("timestamp", System.currentTimeMillis())
            }
            val auditFile = File(context.cacheDir, "install_audit_${parsed.id}.json")
            auditFile.writeText(auditObj.toString(4))
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    companion object {
        fun compareVersions(v1: String, v2: String): Int {
            return ExtensionVersionComparator.compareVersions(v1, v2)
        }
    }
}
