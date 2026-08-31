package com.swift.browser.extensionengine

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ExtensionDirectoryResolver {
    private val idToNameCache = ConcurrentHashMap<String, String>()

    fun cacheIdAndName(id: String, name: String) {
        idToNameCache[id] = name
    }

    fun getExtensionsRootDir(context: Context): File {
        val extFilesDir = context.getExternalFilesDir(null)
        val dir = if (extFilesDir != null) {
            // Under Android/data/package_name/extensions/ (next to files/)
            File(extFilesDir.parentFile, "extensions")
        } else {
            File(context.filesDir, "extensions")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun generateExtensionId(name: String): String {
        return NativeExtensionEngine.generateExtensionId(name)
    }

    fun getExtensionDir(context: Context, id: String, name: String? = null): File {
        val rootDir = getExtensionsRootDir(context)
        
        // 1. Direct match check: if a directory named exactly after 'id' exists, use it instantly.
        val dirById = File(rootDir, id)
        if (dirById.exists() && dirById.isDirectory) {
            return dirById
        }
        
        var resolvedName = name
        if (resolvedName != null) {
            idToNameCache[id] = resolvedName
        } else {
            resolvedName = idToNameCache[id]
        }
        
        if (resolvedName == null) {
            // Also try hardcoded mapping for preloaded/known catalog IDs
            resolvedName = when (id) {
                "ext_grok_automation" -> "Grok Automation"
                "ext_dark_reader" -> "Dark Reader"
                "ext_adblock" -> "AdBlock Plus"
                "ext_metamask" -> "MetaMask Wallet"
                "ext_grok_4" -> "Grok 4.0 AI"
                "ext_cookies" -> "I don't care about cookies"
                "ext_auto_translate" -> "Auto-Translate Extension"
                else -> null
            }
        }
        
        if (resolvedName == null) {
            // Dynamic search of directory if still null
            val subdirs = rootDir.listFiles { f -> f.isDirectory }
            if (subdirs != null) {
                for (subdir in subdirs) {
                    val manifestFile = File(subdir, "manifest.json")
                    if (manifestFile.exists()) {
                        try {
                            val content = manifestFile.readText()
                            val json = org.json.JSONObject(content)
                            val nameInManifest = json.optString("name", "")
                            if (nameInManifest.isNotBlank()) {
                                val calculatedId = generateExtensionId(nameInManifest)
                                if (calculatedId.equals(id, ignoreCase = true) || 
                                    id.equals(nameInManifest, ignoreCase = true) ||
                                    subdir.name.contains(id, ignoreCase = true) ||
                                    id.contains(subdir.name, ignoreCase = true)) {
                                    resolvedName = subdir.name
                                    idToNameCache[id] = resolvedName!!
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
        
        if (resolvedName == null) {
            resolvedName = id
        }
        
        val cleanName = resolvedName.replace("[^a-zA-Z0-9 _.-]".toRegex(), "_").trim()
        val dirByName = File(rootDir, cleanName)
        
        // If name-based folder already exists, prioritize it.
        if (dirByName.exists()) {
            return dirByName
        }
        
        // Creation path:
        // For pre-packaged or catalog extensions (prefixed with 'ext_'), prefer name-based.
        // For custom uploaded or downoaded web store extensions, prefer ID-based to completely avoid localized / dynamic translation rename issues.
        return if (id.startsWith("ext_")) {
            if (!dirByName.exists()) {
                dirByName.mkdirs()
            }
            dirByName
        } else {
            if (!dirById.exists()) {
                dirById.mkdirs()
            }
            dirById
        }
    }

    fun findFileCaseInsensitive(rootDir: File, relativePath: String): File? {
        val cleanPath = try {
            PathSanitizer.sanitizeRelativePath(relativePath)
        } catch (e: Exception) {
            return null
        }
        val directFile = File(rootDir, cleanPath)
        try {
            if (directFile.exists() && directFile.isFile && PathSanitizer.verifyCanonicalContainment(rootDir, directFile.path)) {
                return directFile
            }
        } catch (e: Exception) {}

        // Fallback case-insensitive path segment matching
        val segments = cleanPath.split('/', '\\').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        var currentDir = rootDir
        for (i in segments.indices) {
            val segment = segments[i]
            val children = currentDir.listFiles() ?: return null
            var foundChild: File? = null

            // 1. Exact match
            for (child in children) {
                if (child.name == segment) {
                    foundChild = child
                    break
                }
            }

            // 2. Case-insensitive match 
            if (foundChild == null) {
                for (child in children) {
                    if (child.name.equals(segment, ignoreCase = true)) {
                        foundChild = child
                        break
                    }
                }
            }

            if (foundChild == null) {
                return null // segment not found on disk
            }

            if (i == segments.size - 1) {
                if (!foundChild.isFile) return null
                return if (PathSanitizer.verifyCanonicalContainment(rootDir, foundChild.path)) foundChild else null
            } else {
                currentDir = foundChild
            }
        }
        return null
    }

    var bootstrapProvider: ((String) -> String)? = null

    var globalRegistry: ExtensionRegistry? = null
    var globalPermissionManager: PermissionManager? = null

    fun handleExtensionRequest(
        context: Context,
        urlStr: String,
        isPrivate: Boolean = false
    ): android.webkit.WebResourceResponse? {
        val reg = globalRegistry ?: ExtensionRegistry()
        val pm = globalPermissionManager
        val server = com.swift.browser.extensionengine.resources.ExtensionResourceServer(context, reg, pm)
        return server.handleUrlRequest(urlStr, isPrivate = isPrivate)
    }
}
