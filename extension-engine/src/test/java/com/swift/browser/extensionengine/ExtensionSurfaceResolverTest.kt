package com.swift.browser.extensionengine

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExtensionSurfaceResolverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val extensionId = "abcdefghijklmnopqrstuvwxyz123456"

    @Before
    fun setUp() {
        ExtensionActionAdapter.globalActionStates.clear()
        ExtensionActionAdapter.tabActionStates.clear()
        ExtensionSidePanelAdapter.globalOptions.clear()
        ExtensionSidePanelAdapter.tabOptions.clear()
    }

    @Test
    fun testManifestAuthoritativeActionPopup() {
        // Create extension dir with index.html on disk
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "Test Ext")
        extDir.mkdirs()
        val popupFile = File(extDir, "index.html")
        popupFile.writeText("<html><body>Popup</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "Test Ext",
            version = "1.0",
            manifestVersion = 3,
            actionPopup = "index.html",
            actionSpec = ActionSpec(defaultPopup = "index.html", hasAction = true)
        )

        val surface = ExtensionSurfaceResolver.resolveActionSurface(context, ext)
        assertEquals(ExtensionSurfaceType.ACTION_POPUP, surface.surfaceType)
        assertEquals("index.html", surface.relativePath)
        assertEquals("chrome-extension://$extensionId/index.html", surface.fullUrl)
        assertTrue(surface.isVisibleUi)
    }

    @Test
    fun testActionOnlyWhenNoPopupDeclared() {
        val ext = ParsedExtension(
            id = extensionId,
            name = "Action Only Ext",
            version = "1.0",
            manifestVersion = 3,
            actionSpec = ActionSpec(defaultPopup = "", hasAction = true)
        )

        val surface = ExtensionSurfaceResolver.resolveActionSurface(context, ext)
        assertEquals(ExtensionSurfaceType.ACTION_ONLY, surface.surfaceType)
        assertFalse(surface.isVisibleUi)
    }

    @Test
    fun testNoActionWhenNoActionDeclaredInManifest() {
        // Even if popup.html exists on disk, if manifest has no action, resolver MUST NOT invent popup!
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "Background Only Ext")
        extDir.mkdirs()
        File(extDir, "popup.html").writeText("<html><body>Undeclared</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "Background Only Ext",
            version = "1.0",
            manifestVersion = 3,
            actionSpec = ActionSpec(defaultPopup = "", hasAction = false)
        )

        val surface = ExtensionSurfaceResolver.resolveActionSurface(context, ext)
        assertEquals(ExtensionSurfaceType.NONE, surface.surfaceType)
    }

    @Test
    fun testRuntimeSetPopupOverride() {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "Runtime Action Ext")
        extDir.mkdirs()
        File(extDir, "dynamic.html").writeText("<html><body>Dynamic</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "Runtime Action Ext",
            version = "1.0",
            manifestVersion = 3,
            actionSpec = ActionSpec(defaultPopup = "", hasAction = true)
        )

        // Set dynamic popup via ExtensionActionAdapter
        ExtensionActionAdapter.globalActionStates[extensionId] = ExtensionActionState(extensionId = extensionId, popupPath = "dynamic.html")

        val surface = ExtensionSurfaceResolver.resolveActionSurface(context, ext)
        assertEquals(ExtensionSurfaceType.ACTION_POPUP, surface.surfaceType)
        assertEquals("dynamic.html", surface.relativePath)
    }

    @Test
    fun testRuntimeSetPopupClearedToEmptyString() {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "Runtime Clear Ext")
        extDir.mkdirs()
        File(extDir, "manifest_popup.html").writeText("<html><body>Manifest</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "Runtime Clear Ext",
            version = "1.0",
            manifestVersion = 3,
            actionPopup = "manifest_popup.html",
            actionSpec = ActionSpec(defaultPopup = "manifest_popup.html", hasAction = true)
        )

        // Dynamic API cleared popup to empty string
        ExtensionActionAdapter.globalActionStates[extensionId] = ExtensionActionState(extensionId = extensionId, popupPath = "")

        val surface = ExtensionSurfaceResolver.resolveActionSurface(context, ext)
        assertEquals(ExtensionSurfaceType.ACTION_ONLY, surface.surfaceType)
    }

    @Test
    fun testSidePanelSurfaceResolution() {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "SidePanel Ext")
        extDir.mkdirs()
        File(extDir, "sidepanel.html").writeText("<html><body>SidePanel</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "SidePanel Ext",
            version = "1.0",
            manifestVersion = 3,
            sidePanelPath = "sidepanel.html"
        )

        val surface = ExtensionSurfaceResolver.resolveSidePanelSurface(context, ext)
        assertEquals(ExtensionSurfaceType.SIDE_PANEL, surface.surfaceType)
        assertEquals("sidepanel.html", surface.relativePath)
        assertEquals("chrome-extension://$extensionId/sidepanel.html", surface.fullUrl)
    }

    @Test
    fun testOptionsPageSurfaceResolution() {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "Options Ext")
        extDir.mkdirs()
        File(extDir, "options.html").writeText("<html><body>Options</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "Options Ext",
            version = "1.0",
            manifestVersion = 3,
            optionsPage = "options.html",
            optionsInTab = true
        )

        val surface = ExtensionSurfaceResolver.resolveOptionsSurface(context, ext)
        assertEquals(ExtensionSurfaceType.OPTIONS_PAGE, surface.surfaceType)
        assertEquals("options.html", surface.relativePath)
        assertTrue(surface.openInTab)
    }

    @Test
    fun testDevToolsSurfaceResolution() {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "DevTools Ext")
        extDir.mkdirs()
        File(extDir, "devtools.html").writeText("<html><body>DevTools</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "DevTools Ext",
            version = "1.0",
            manifestVersion = 3,
            devtoolsPagePath = "devtools.html"
        )

        val surface = ExtensionSurfaceResolver.resolveDevToolsSurface(context, ext)
        assertEquals(ExtensionSurfaceType.DEVTOOLS_PANEL, surface.surfaceType)
        assertEquals("devtools.html", surface.relativePath)
    }

    @Test
    fun testUrlOverrideSurfaceResolution() {
        val extDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId, "Url Override Ext")
        extDir.mkdirs()
        File(extDir, "newtab.html").writeText("<html><body>NewTab</body></html>")

        val ext = ParsedExtension(
            id = extensionId,
            name = "Url Override Ext",
            version = "1.0",
            manifestVersion = 3,
            urlOverrides = mapOf("newtab" to "newtab.html")
        )

        val surface = ExtensionSurfaceResolver.resolveUrlOverrideSurface(context, ext, "newtab")
        assertEquals(ExtensionSurfaceType.URL_OVERRIDE, surface.surfaceType)
        assertEquals("newtab.html", surface.relativePath)
        assertEquals("newtab", surface.overrideType)
    }

    @Test
    fun testContentScriptOnlyNoVisibleUi() {
        val ext = ParsedExtension(
            id = extensionId,
            name = "Content Script Only",
            version = "1.0",
            manifestVersion = 3,
            contentScripts = listOf(ContentScriptSpec(matches = listOf("<all_urls>"), js = listOf("content.js"), css = emptyList()))
        )

        val surfaces = ExtensionSurfaceResolver.resolveAllSurfaces(context, ext)
        assertEquals(1, surfaces.size)
        assertEquals(ExtensionSurfaceType.CONTENT_SCRIPT, surfaces[0].surfaceType)
        assertFalse(surfaces[0].isVisibleUi)
    }
}
