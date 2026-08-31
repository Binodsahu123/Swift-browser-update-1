package com.swift.browser.extensionengine

import android.content.Context
import android.util.Log
import com.swift.browser.extensionengine.origin.ExtensionUrl
import java.io.File

object ExtensionSurfaceResolver {

    private const val TAG = "EXT_SURFACE"

    /**
     * Resolves the surface to launch when user clicks the extension's action icon.
     * Manifest definition is authoritative. Runtime setPopup overrides manifest.
     */
    fun resolveActionSurface(context: Context, ext: ParsedExtension, tabId: String? = null): ResolvedExtensionSurface {
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)

        // 1. Check dynamic runtime popup state from ExtensionActionAdapter
        val stateKey = if (!tabId.isNullOrBlank()) "${ext.id}_$tabId" else null
        val tabState = if (stateKey != null) ExtensionActionAdapter.tabActionStates[stateKey] else null
        val globalState = ExtensionActionAdapter.globalActionStates[ext.id]

        val runtimePopupPath = tabState?.popupPath ?: globalState?.popupPath

        val (effectivePopup, source) = if (runtimePopupPath != null) {
            Pair(runtimePopupPath, "runtime")
        } else {
            val declared = ext.actionPopup.ifBlank { ext.popupPath }.ifBlank { ext.actionSpec.defaultPopup }
            Pair(declared, "manifest")
        }

        if (effectivePopup.isNotBlank()) {
            val cleanPath = effectivePopup.removePrefix("/").removePrefix("./")
            val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, cleanPath)
            if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                val relPath = getRelativePath(extensionDir, targetFile)
                val surface = ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.ACTION_POPUP,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id
                )
                Log.d(TAG, "[EXT_SURFACE] Resolved ACTION_POPUP extensionId=${ext.id} path=$relPath source=$source tabId=$tabId")
                return surface
            } else {
                Log.w(TAG, "[EXT_SURFACE] Declared action popup file missing on disk extensionId=${ext.id} path=$cleanPath source=$source")
                return ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.NONE,
                    relativePath = "",
                    fullUrl = "",
                    extensionId = ext.id
                )
            }
        }

        // 2. Action exists in manifest (action / browser_action / page_action block) but no popup HTML path declared
        val hasActionDeclared = ext.actionSpec.hasAction || ext.actionPopup.isNotBlank() || ext.popupPath.isNotBlank()
        if (hasActionDeclared) {
            val surface = ResolvedExtensionSurface(
                surfaceType = ExtensionSurfaceType.ACTION_ONLY,
                relativePath = "",
                fullUrl = "",
                extensionId = ext.id
            )
            Log.d(TAG, "[EXT_SURFACE] Resolved ACTION_ONLY extensionId=${ext.id} source=$source tabId=$tabId")
            return surface
        }

        Log.d(TAG, "[EXT_SURFACE] No action surface defined extensionId=${ext.id}")
        return ResolvedExtensionSurface(
            surfaceType = ExtensionSurfaceType.NONE,
            relativePath = "",
            fullUrl = "",
            extensionId = ext.id
        )
    }

    /**
     * Resolves side panel surface for extension. Manifest side_panel or runtime setOptions.
     */
    fun resolveSidePanelSurface(context: Context, ext: ParsedExtension, tabId: String? = null): ResolvedExtensionSurface {
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)

        val stateKey = if (!tabId.isNullOrBlank()) "${ext.id}_$tabId" else null
        val tabOpts = if (stateKey != null && ExtensionSidePanelAdapter.tabOptions.containsKey(stateKey)) ExtensionSidePanelAdapter.tabOptions[stateKey] else null
        val globalOpts = ExtensionSidePanelAdapter.globalOptions[ext.id]

        val runtimeOpts = tabOpts ?: globalOpts
        if (runtimeOpts != null && !runtimeOpts.enabled) {
            Log.d(TAG, "[EXT_SURFACE] Side panel disabled via runtime setOptions extensionId=${ext.id}")
            return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
        }

        val runtimePath = runtimeOpts?.path
        val (effectivePath, source) = if (!runtimePath.isNullOrBlank()) {
            Pair(runtimePath, "runtime")
        } else {
            Pair(ext.sidePanelPath, "manifest")
        }

        if (effectivePath.isNotBlank()) {
            val cleanPath = effectivePath.removePrefix("/").removePrefix("./")
            val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, cleanPath)
            if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                val relPath = getRelativePath(extensionDir, targetFile)
                val surface = ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.SIDE_PANEL,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id
                )
                Log.d(TAG, "[EXT_SURFACE] Resolved SIDE_PANEL extensionId=${ext.id} path=$relPath source=$source tabId=$tabId")
                return surface
            } else {
                Log.w(TAG, "[EXT_SURFACE] Declared side panel file missing on disk extensionId=${ext.id} path=$cleanPath source=$source")
            }
        }

        return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
    }

    /**
     * Resolves options page surface for extension. Manifest options_page or options_ui.page.
     */
    fun resolveOptionsSurface(context: Context, ext: ParsedExtension): ResolvedExtensionSurface {
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)

        val declared = ext.optionsPage
        if (declared.isNotBlank()) {
            val cleanPath = declared.removePrefix("/").removePrefix("./")
            val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, cleanPath)
            if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                val relPath = getRelativePath(extensionDir, targetFile)
                val surface = ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.OPTIONS_PAGE,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id,
                    openInTab = ext.optionsInTab
                )
                Log.d(TAG, "[EXT_SURFACE] Resolved OPTIONS_PAGE extensionId=${ext.id} path=$relPath openInTab=${ext.optionsInTab}")
                return surface
            } else {
                Log.w(TAG, "[EXT_SURFACE] Declared options page file missing on disk extensionId=${ext.id} path=$cleanPath")
            }
        }

        return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
    }

    /**
     * Resolves DevTools panel surface for extension. Manifest devtools_page.
     */
    fun resolveDevToolsSurface(context: Context, ext: ParsedExtension): ResolvedExtensionSurface {
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)

        val declared = ext.devtoolsPagePath
        if (declared.isNotBlank()) {
            val cleanPath = declared.removePrefix("/").removePrefix("./")
            val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, cleanPath)
            if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                val relPath = getRelativePath(extensionDir, targetFile)
                val surface = ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.DEVTOOLS_PANEL,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id
                )
                Log.d(TAG, "[EXT_SURFACE] Resolved DEVTOOLS_PANEL extensionId=${ext.id} path=$relPath")
                return surface
            } else {
                Log.w(TAG, "[EXT_SURFACE] Declared devtools page file missing on disk extensionId=${ext.id} path=$cleanPath")
            }
        }

        return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
    }

    /**
     * Resolves URL override surface (e.g. "newtab", "bookmarks", "history").
     */
    fun resolveUrlOverrideSurface(context: Context, ext: ParsedExtension, overrideType: String): ResolvedExtensionSurface {
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)

        val declared = ext.urlOverrides[overrideType] ?: ""
        if (declared.isNotBlank()) {
            val cleanPath = declared.removePrefix("/").removePrefix("./")
            val targetFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, cleanPath)
            if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                val relPath = getRelativePath(extensionDir, targetFile)
                val surface = ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.URL_OVERRIDE,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id,
                    overrideType = overrideType
                )
                Log.d(TAG, "[EXT_SURFACE] Resolved URL_OVERRIDE type=$overrideType extensionId=${ext.id} path=$relPath")
                return surface
            } else {
                Log.w(TAG, "[EXT_SURFACE] Declared URL override file missing on disk type=$overrideType extensionId=${ext.id} path=$cleanPath")
            }
        }

        return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
    }

    /**
     * Resolves background execution surface (Service Worker or Background Page).
     * Note: isVisibleUi = false.
     */
    fun resolveBackgroundSurface(context: Context, ext: ParsedExtension): ResolvedExtensionSurface {
        if (ext.isServiceWorker || ext.backgroundSpec.serviceWorker.isNotBlank()) {
            val relPath = ext.backgroundPath.ifBlank { ext.backgroundSpec.serviceWorker }
            if (relPath.isNotBlank()) {
                return ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.SERVICE_WORKER,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id
                )
            }
        } else if (ext.backgroundSpec.page.isNotBlank()) {
            val relPath = ext.backgroundPath.ifBlank { ext.backgroundSpec.page }
            if (relPath.isNotBlank()) {
                return ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.BACKGROUND_PAGE,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id
                )
            }
        } else if (ext.backgroundSpec.scripts.isNotEmpty() || ext.backgroundScripts.isNotEmpty() || ext.backgroundPath.isNotBlank()) {
            val scripts = ext.backgroundSpec.scripts.ifEmpty { ext.backgroundScripts }
            val relPath = ext.backgroundPath.ifBlank { scripts.firstOrNull() ?: "" }
            if (relPath.isNotBlank()) {
                return ResolvedExtensionSurface(
                    surfaceType = ExtensionSurfaceType.BACKGROUND_SCRIPTS,
                    relativePath = relPath,
                    fullUrl = ExtensionUrl.toExtensionUrl(ext.id, relPath),
                    extensionId = ext.id
                )
            }
        }
        return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
    }

    /**
     * Resolves content script surface.
     * Note: isVisibleUi = false.
     */
    fun resolveContentScriptSurface(context: Context, ext: ParsedExtension): ResolvedExtensionSurface {
        if (ext.contentScripts.isNotEmpty()) {
            return ResolvedExtensionSurface(
                surfaceType = ExtensionSurfaceType.CONTENT_SCRIPT,
                relativePath = "",
                fullUrl = "",
                extensionId = ext.id
            )
        }
        return ResolvedExtensionSurface(ExtensionSurfaceType.NONE, "", "", ext.id)
    }

    /**
     * Resolves all active declared/runtime surfaces for an extension.
     */
    fun resolveAllSurfaces(context: Context, ext: ParsedExtension, tabId: String? = null): List<ResolvedExtensionSurface> {
        val list = mutableListOf<ResolvedExtensionSurface>()

        val action = resolveActionSurface(context, ext, tabId)
        if (action.surfaceType != ExtensionSurfaceType.NONE) list.add(action)

        val sidePanel = resolveSidePanelSurface(context, ext, tabId)
        if (sidePanel.surfaceType != ExtensionSurfaceType.NONE) list.add(sidePanel)

        val options = resolveOptionsSurface(context, ext)
        if (options.surfaceType != ExtensionSurfaceType.NONE) list.add(options)

        val devtools = resolveDevToolsSurface(context, ext)
        if (devtools.surfaceType != ExtensionSurfaceType.NONE) list.add(devtools)

        for (type in listOf("newtab", "bookmarks", "history")) {
            val override = resolveUrlOverrideSurface(context, ext, type)
            if (override.surfaceType != ExtensionSurfaceType.NONE) list.add(override)
        }

        val bg = resolveBackgroundSurface(context, ext)
        if (bg.surfaceType != ExtensionSurfaceType.NONE) list.add(bg)

        val cs = resolveContentScriptSurface(context, ext)
        if (cs.surfaceType != ExtensionSurfaceType.NONE) list.add(cs)

        return list
    }

    private fun getRelativePath(baseDir: File, targetFile: File): String {
        return try {
            targetFile.relativeTo(baseDir).path.replace('\\', '/')
        } catch (e: Exception) {
            targetFile.name
        }
    }
}
