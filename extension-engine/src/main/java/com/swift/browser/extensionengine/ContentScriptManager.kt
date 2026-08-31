package com.swift.browser.extensionengine

import android.content.Context
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.JavaScriptExecutionWorld
import com.swift.browser.extensionengine.resources.ExtensionResourceResolver
import com.swift.browser.extensionengine.resources.ExtensionResourceResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Error Model Constants
const val CONTENT_SCRIPT_RESOURCE_NOT_FOUND = "CONTENT_SCRIPT_RESOURCE_NOT_FOUND"
const val CONTENT_SCRIPT_PERMISSION_DENIED = "CONTENT_SCRIPT_PERMISSION_DENIED"
const val CONTENT_SCRIPT_HOST_DENIED = "CONTENT_SCRIPT_HOST_DENIED"
const val CONTENT_SCRIPT_DISABLED = "CONTENT_SCRIPT_DISABLED"
const val CONTENT_SCRIPT_PRIVATE_BLOCKED = "CONTENT_SCRIPT_PRIVATE_BLOCKED"
const val CONTENT_SCRIPT_UNSUPPORTED_WORLD = "CONTENT_SCRIPT_UNSUPPORTED_WORLD"
const val CONTENT_SCRIPT_UNSUPPORTED_TIMING = "CONTENT_SCRIPT_UNSUPPORTED_TIMING"
const val CONTENT_SCRIPT_FRAME_UNAVAILABLE = "CONTENT_SCRIPT_FRAME_UNAVAILABLE"
const val CONTENT_SCRIPT_DOCUMENT_STALE = "CONTENT_SCRIPT_DOCUMENT_STALE"
const val CONTENT_SCRIPT_WEBVIEW_DESTROYED = "CONTENT_SCRIPT_WEBVIEW_DESTROYED"
const val CONTENT_SCRIPT_EXECUTION_FAILED = "CONTENT_SCRIPT_EXECUTION_FAILED"

data class ContentScriptErrorReport(
    val extensionId: String,
    val scriptId: String,
    val errorType: String,
    val errorMsg: String,
    val timestamp: Long,
    val webViewInstanceId: Int,
    val uri: String,
    val targetFrameId: Int,
    val failedStep: String
)

data class ContentScriptInjectionState(
    val webViewInstanceId: Int,
    val extensionId: String,
    val scriptId: String,
    val navigationGeneration: Int,
    val registered: Boolean,
    val injectedFrames: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>(),
    val failedFrames: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>()
)

data class DynamicContentScriptSpec(
    val id: String,
    val matches: List<String>,
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    val runAt: String = "document_idle",
    val allFrames: Boolean = false,
    val world: String = "ISOLATED"
)

class ContentScriptManager(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val scriptInjector: ScriptInjector,
    private val cssInjector: CssInjector,
    private val extensionRegistry: ExtensionRegistry? = null,
    private val generationLookup: (String) -> Int = { 0 }
) {
    val registry = ContentScriptRegistry()

    // Tracks ScriptHandlers registered on WebView objects to clean up on WebView destruction
    private val webViewScriptHandlers = ConcurrentHashMap<Int, MutableList<androidx.webkit.ScriptHandler>>()

    // Tracks injected script execution keys per documentId/webView
    private val injectedTracker = ConcurrentHashMap<String, MutableSet<String>>()

    // Tracks current navigation generation per WebView hash
    private val navigationGenerations = ConcurrentHashMap<Int, Int>()

    // Bookkeeping of content script registration/injection states per WebView
    private val scriptInjectionStates = ConcurrentHashMap<Int, MutableList<ContentScriptInjectionState>>()

    // Standardized error report log
    private val errorReports = mutableListOf<ContentScriptErrorReport>()

    private val resourceResolver by lazy {
        ExtensionResourceResolver(context, extensionRegistry ?: ExtensionRegistry(), permissionManager)
    }

    fun getLastError(): ContentScriptErrorReport? {
        return synchronized(errorReports) {
            errorReports.lastOrNull()
        }
    }

    fun getErrorReports(): List<ContentScriptErrorReport> {
        return synchronized(errorReports) {
            errorReports.toList()
        }
    }

    fun reportError(report: ContentScriptErrorReport) {
        synchronized(errorReports) {
            errorReports.add(report)
            if (errorReports.size > 100) {
                errorReports.removeAt(0)
            }
        }
        android.util.Log.e("ContentScriptManager", "Content script injection failed: $report")
    }

    fun getInjectionStatesForWebView(webViewInstanceId: Int): List<ContentScriptInjectionState> {
        return scriptInjectionStates[webViewInstanceId] ?: emptyList()
    }

    private fun isExtensionActive(extensionId: String): Boolean {
        val reg = extensionRegistry ?: return true
        return reg.isExtensionEnabled(extensionId)
    }

    private fun getActiveDefinitions(): List<ContentScriptDefinition> {
        return registry.getAllActiveDefinitions().filter { isExtensionActive(it.extensionId) }
    }

    fun updateInjectionState(
        webViewInstanceId: Int,
        extensionId: String,
        scriptId: String,
        navigationGen: Int,
        registered: Boolean,
        successFrame: Int? = null,
        failedFrame: Int? = null
    ) {
        val list = scriptInjectionStates.getOrPut(webViewInstanceId) { java.util.Collections.synchronizedList(mutableListOf()) }
        val existing = list.find { it.extensionId == extensionId && it.scriptId == scriptId && it.navigationGeneration == navigationGen }
        if (existing != null) {
            if (successFrame != null) {
                existing.injectedFrames.add(successFrame)
            }
            if (failedFrame != null) {
                existing.failedFrames.add(failedFrame)
            }
        } else {
            val newState = ContentScriptInjectionState(
                webViewInstanceId = webViewInstanceId,
                extensionId = extensionId,
                scriptId = scriptId,
                navigationGeneration = navigationGen,
                registered = registered
            )
            if (successFrame != null) {
                newState.injectedFrames.add(successFrame)
            }
            if (failedFrame != null) {
                newState.failedFrames.add(failedFrame)
            }
            list.add(newState)
        }
    }

    fun onPageStarted(webView: android.webkit.WebView, url: String) {
        val hash = webView.hashCode()
        val nextGen = (navigationGenerations[hash] ?: 0) + 1
        navigationGenerations[hash] = nextGen

        val isEligible = !url.startsWith("swift:") && !url.startsWith("chrome://") && !url.startsWith("file://")
        if (isEligible) {
            val activeDefinitions = getActiveDefinitions()
            for (def in activeDefinitions) {
                if (def.matchesUrl(url)) {
                    updateInjectionState(
                        webViewInstanceId = hash,
                        extensionId = def.extensionId,
                        scriptId = def.scriptId,
                        navigationGen = nextGen,
                        registered = true
                    )
                }
            }
        }

        clearHistoryForWebView(hash.toString())
    }

    fun registerContentScripts(extensionId: String, scripts: List<DynamicContentScriptSpec>) {
        for (spec in scripts) {
            val scriptId = generateDynamicScriptId(extensionId, spec.id)
            val definition = ContentScriptDefinition(
                extensionId = extensionId,
                scriptId = scriptId,
                jsFiles = spec.js,
                cssFiles = spec.css,
                matches = spec.matches,
                runAt = spec.runAt,
                allFrames = spec.allFrames,
                world = spec.world,
                persist = true,
                enabled = true
            )
            registry.register(definition)
        }
        registry.incrementGeneration()
    }

    fun unregisterContentScripts(extensionId: String, ids: List<String>?) {
        if (ids == null || ids.isEmpty()) {
            registry.unregisterAllForExtension(extensionId)
        } else {
            for (id in ids) {
                registry.unregister(generateDynamicScriptId(extensionId, id))
            }
        }
        registry.incrementGeneration()
    }

    fun updateContentScripts(extensionId: String, scripts: List<DynamicContentScriptSpec>) {
        registerContentScripts(extensionId, scripts)
    }

    fun getRegisteredContentScripts(extensionId: String): List<DynamicContentScriptSpec> {
        val definitions = registry.getDefinitionsForExtension(extensionId)
        return definitions.map { def ->
            val cleanId = def.scriptId.removePrefix("${extensionId}_dyn_")
            DynamicContentScriptSpec(
                id = cleanId,
                matches = def.matches,
                js = def.jsFiles,
                css = def.cssFiles,
                runAt = def.runAt,
                allFrames = def.allFrames,
                world = def.world
            )
        }
    }

    fun registerManifestScripts(ext: ParsedExtension) {
        ext.contentScripts.forEachIndexed { index, spec ->
            val scriptId = generateScriptId(ext.id, index, spec)
            val definition = ContentScriptDefinition(
                extensionId = ext.id,
                scriptId = scriptId,
                jsFiles = spec.js,
                cssFiles = spec.css,
                matches = spec.matches,
                excludeMatches = spec.excludeMatches,
                includeGlobs = spec.includeGlobs,
                excludeGlobs = spec.excludeGlobs,
                runAt = spec.runAt,
                allFrames = spec.allFrames,
                matchAboutBlank = spec.matchAboutBlank,
                matchOriginAsFallback = spec.matchOriginAsFallback,
                world = spec.world,
                persist = true,
                enabled = true
            )
            registry.register(definition)
        }
        registry.incrementGeneration()
    }

    fun generateScriptId(extensionId: String, index: Int, spec: ContentScriptSpec): String {
        val specString = "${spec.matches.joinToString()}|${spec.js.joinToString()}|${spec.css.joinToString()}|${spec.runAt}|${spec.allFrames}|${spec.world}"
        val hash = specString.hashCode()
        return "${extensionId}_cs_${index}_$hash"
    }

    fun generateDynamicScriptId(extensionId: String, id: String): String {
        return "${extensionId}_dyn_$id"
    }

    fun clearHistoryForWebView(webViewHash: String) {
        injectedTracker.remove(webViewHash)
    }

    fun clearHistoryForDocument(documentId: String) {
        injectedTracker.remove(documentId)
    }

    fun cleanUpWebView(webView: android.webkit.WebView) {
        val hash = webView.hashCode()
        webViewScriptHandlers.remove(hash)?.forEach { handler ->
            try {
                handler.remove()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        injectedTracker.remove(hash.toString())
        scriptInjectionStates.remove(hash)
        navigationGenerations.remove(hash)
    }

    /**
     * Manifest-driven Content Script execution engine.
     * Evaluates specs against URL pattern matching, host permissions, frame targets, and lifecycle phases.
     */
    fun matchAndInject(
        evaluator: ScriptEvaluator,
        url: String,
        parsedExtensions: List<ParsedExtension>,
        runAtFilter: String,
        bootstrapScriptProvider: (String) -> String,
        isPrivate: Boolean = false,
        privateSessionId: String? = null,
        tabId: String? = null,
        frameId: Int = 0,
        documentId: String? = null,
        targetOrigin: String? = null
    ): String {
        if (url.startsWith("swift:") || url.startsWith("chrome://")) return "SUCCESS"

        // 1. Ensure all enabled extensions have their manifest scripts registered in our registry
        for (ext in parsedExtensions) {
            if (registry.getDefinitionsForExtension(ext.id).isEmpty() && ext.contentScripts.isNotEmpty()) {
                registerManifestScripts(ext)
            }
        }

        // 2. Decide if we use native persistent script injection
        // If native persistent script injection is supported for this phase, we skip manual injection!
        val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        val frameAndWorldSupported = WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)

        val runAtLower = runAtFilter.lowercase().trim()

        if (runAtLower == "document_start" && documentStartSupported) {
            // Handled natively by WebViewCompat.addDocumentStartJavaScript!
            return "NATIVE_INJECTION_SCHEDULED"
        }
        if (runAtLower == "document_end" && frameAndWorldSupported) {
            // Handled natively by WebViewCompat.addJavaScriptOnEvent!
            return "NATIVE_INJECTION_SCHEDULED"
        }
        if (runAtLower == "document_idle" && frameAndWorldSupported) {
            // Handled natively by WebViewCompat.addJavaScriptOnEvent!
            return "NATIVE_INJECTION_SCHEDULED"
        }

        // If runAt is document_start and feature is unsupported, we must NOT fall back to evaluateJavascript
        if (runAtLower == "document_start") {
            val errorMsg = "DOCUMENT_START is unsupported on this WebView version"
            reportError(ContentScriptErrorReport(
                extensionId = "system",
                scriptId = "system_document_start",
                errorType = "CONTENT_SCRIPT_UNSUPPORTED_TIMING",
                errorMsg = errorMsg,
                timestamp = System.currentTimeMillis(),
                webViewInstanceId = evaluator.hashCode(),
                uri = url,
                targetFrameId = frameId,
                failedStep = "fallback_check"
            ))
            return "DOCUMENT_START_UNAVAILABLE"
        }

        val status = when (runAtLower) {
            "document_end" -> "DOCUMENT_END_APPROXIMATION"
            "document_idle" -> "DOCUMENT_IDLE_APPROXIMATION"
            else -> "SUCCESS"
        }

        // Otherwise, execute fallback manual injection (approximate document_end / document_idle)
        val targetScripts = registry.getAllActiveDefinitions().filter { def ->
            def.runAt.lowercase().trim() == runAtLower
        }

        for (def in targetScripts) {
            val ext = parsedExtensions.find { it.id == def.extensionId }
                ?: extensionRegistry?.getExtension(def.extensionId)
                ?: continue

            // Pre-injection Check 1: Extension enabled
            if (extensionRegistry != null && !extensionRegistry.isExtensionEnabled(ext.id)) {
                reportError(ContentScriptErrorReport(
                    extensionId = ext.id,
                    scriptId = def.scriptId,
                    errorType = "CONTENT_SCRIPT_DISABLED",
                    errorMsg = "Extension is disabled: ${ext.id}",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = evaluator.hashCode(),
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "pre_injection_check_enabled"
                ))
                continue
            }

            // Pre-injection Check 2: Manifest validity
            if (ext.id.isBlank() || ext.manifestVersion < 2) {
                continue
            }

            // Pre-injection Check 3: Private mode policy
            if (isPrivate && !ext.allowedInPrivate && !permissionManager.isAllowedInPrivate(ext.id)) {
                reportError(ContentScriptErrorReport(
                    extensionId = ext.id,
                    scriptId = def.scriptId,
                    errorType = "CONTENT_SCRIPT_PRIVATE_BLOCKED",
                    errorMsg = "Extension is not allowed in private mode: ${ext.id}",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = evaluator.hashCode(),
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "pre_injection_check_private"
                ))
                continue
            }

            // Check all_frames targeting filter
            if (frameId > 0 && !def.allFrames) {
                reportError(ContentScriptErrorReport(
                    extensionId = ext.id,
                    scriptId = def.scriptId,
                    errorType = "CONTENT_SCRIPT_FRAME_UNAVAILABLE",
                    errorMsg = "Script does not target subframes: ${def.scriptId}",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = evaluator.hashCode(),
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "pre_injection_check_frame"
                ))
                continue
            }

            // Pre-injection Check 4: URL match pattern, globs, exclude matches, match_about_blank
            if (!def.matchesUrl(url, targetOrigin)) {
                reportError(ContentScriptErrorReport(
                    extensionId = ext.id,
                    scriptId = def.scriptId,
                    errorType = "CONTENT_SCRIPT_HOST_DENIED",
                    errorMsg = "URL does not match script patterns: $url",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = evaluator.hashCode(),
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "pre_injection_check_url"
                ))
                continue
            }

            // Pre-injection Check 5: Host permission validation
            val hasPermission = permissionManager.hasHostPermission(ext.id, ext.hostPermissions, ext.permissions, url) ||
                    permissionManager.hasHostPermission(ext.id, def.matches, emptyList(), url)

            if (!hasPermission) {
                reportError(ContentScriptErrorReport(
                    extensionId = ext.id,
                    scriptId = def.scriptId,
                    errorType = "CONTENT_SCRIPT_PERMISSION_DENIED",
                    errorMsg = "Missing host permission for URL: $url",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = evaluator.hashCode(),
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "pre_injection_check_permission"
                ))
                continue
            }

            val generation = generationLookup(ext.id)
            val docToken = documentId ?: "default"

            // 1. CSS Fallback Injection
            if (runAtLower == "document_end" || runAtLower == "document_idle") {
                for (cssPath in def.cssFiles) {
                    try {
                        val cssContent = readExtensionFile(
                            extensionId = ext.id,
                            relativePath = cssPath,
                            isPrivate = isPrivate,
                            webViewHash = evaluator.hashCode(),
                            url = url,
                            frameId = frameId,
                            scriptId = def.scriptId
                        )
                        if (cssContent.isNotBlank()) {
                            val cssKey = "style_${ext.id}_${cssPath.hashCode()}_${generation}_$docToken"
                            val guardAndInjectScript = """
                                (function() {
                                    window._swiftStylesInjected = window._swiftStylesInjected || {};
                                    if (window._swiftStylesInjected["$cssKey"]) return;
                                    window._swiftStylesInjected["$cssKey"] = true;
                                    try {
                                         const style = document.createElement('style');
                                         style.type = 'text/css';
                                         style.innerHTML = ${org.json.JSONObject.quote(cssContent)};
                                         (document.head || document.documentElement).appendChild(style);
                                    } catch(e) {
                                         console.error("CSS Injection Error in $cssPath: ", e);
                                    }
                                })();
                            """.trimIndent()
                            scriptInjector.injectScript(evaluator, guardAndInjectScript)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 2. JS Fallback Injection
            // For document_idle, implement controlled post-load scheduling strategy
            if (runAtLower == "document_idle") {
                evaluator.post {
                    injectJsFallback(evaluator, def, ext, url, frameId, isPrivate, generation, docToken, bootstrapScriptProvider)
                }
            } else {
                injectJsFallback(evaluator, def, ext, url, frameId, isPrivate, generation, docToken, bootstrapScriptProvider)
            }
        }
        return status
    }

    private fun injectJsFallback(
        evaluator: ScriptEvaluator,
        def: ContentScriptDefinition,
        ext: ParsedExtension,
        url: String,
        frameId: Int,
        isPrivate: Boolean,
        generation: Int,
        docToken: String,
        bootstrapScriptProvider: (String) -> String
    ) {
        for (jsPath in def.jsFiles) {
            try {
                val jsContent = readExtensionFile(
                    extensionId = ext.id,
                    relativePath = jsPath,
                    isPrivate = isPrivate,
                    webViewHash = evaluator.hashCode(),
                    url = url,
                    frameId = frameId,
                    scriptId = def.scriptId
                )
                if (jsContent.isNotBlank()) {
                    val jsKey = "script_${ext.id}_${jsPath.hashCode()}_${generation}_$docToken"
                    val bootScript = bootstrapScriptProvider(ext.id)
                    
                    // Unprivileged web page execution container
                    val selfContainedScopedScript = """
                        (function() {
                            window._swiftScriptsInjected = window._swiftScriptsInjected || {};
                            if (window._swiftScriptsInjected["$jsKey"]) return;
                            window._swiftScriptsInjected["$jsKey"] = true;
                            
                            // Ensure extension context APIs are initialized safely without exposing native Java bridges
                            if (typeof window._swiftGetExtensionContext === 'undefined') {
                                try {
                                    $bootScript
                                } catch(e) {
                                    console.error("API Bootstrap Failed for content script: ", e);
                                }
                            }
                            
                            try {
                                const browser = window._swiftGetExtensionContext("${ext.id}");
                                const chrome = browser;
                                
                                const windowProxy = new Proxy(globalThis, {
                                    get(target, prop) {
                                        if (prop === 'chrome') return chrome;
                                        if (prop === 'browser') return browser;
                                        if (prop === 'window' || prop === 'self' || prop === 'globalThis') return windowProxy;
                                        let val = target[prop];
                                        if (typeof val === 'function') {
                                            try {
                                                return val.bind(target);
                                            } catch(e) {
                                                return val;
                                            }
                                        }
                                        return val;
                                    }
                                });
                                const window = windowProxy;
                                const self = windowProxy;
                                const globalThis = windowProxy;
                                
                                $jsContent
                            } catch (e) {
                                console.error("Content Script Exec Error in $jsPath: ", e);
                            }
                        })();
                    """.trimIndent()

                    val startGen = navigationGenerations[evaluator.hashCode()] ?: 0
                    scriptInjector.injectScript(evaluator, selfContainedScopedScript) { value ->
                        val endGen = navigationGenerations[evaluator.hashCode()] ?: 0
                        if (startGen != endGen) {
                            reportError(ContentScriptErrorReport(
                                extensionId = ext.id,
                                scriptId = def.scriptId,
                                errorType = "CONTENT_SCRIPT_DOCUMENT_STALE",
                                errorMsg = "Navigation occurred during injection. Generation changed from $startGen to $endGen",
                                timestamp = System.currentTimeMillis(),
                                webViewInstanceId = evaluator.hashCode(),
                                uri = url,
                                targetFrameId = frameId,
                                failedStep = "callback_received"
                            ))
                            updateInjectionState(
                                webViewInstanceId = evaluator.hashCode(),
                                extensionId = ext.id,
                                scriptId = def.scriptId,
                                navigationGen = startGen,
                                registered = true,
                                failedFrame = frameId
                            )
                        } else {
                            updateInjectionState(
                                webViewInstanceId = evaluator.hashCode(),
                                extensionId = ext.id,
                                scriptId = def.scriptId,
                                navigationGen = startGen,
                                registered = true,
                                successFrame = frameId
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                reportError(ContentScriptErrorReport(
                    extensionId = ext.id,
                    scriptId = def.scriptId,
                    errorType = "CONTENT_SCRIPT_EXECUTION_FAILED",
                    errorMsg = e.message ?: "JS Injection failed",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = evaluator.hashCode(),
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "inject_javascript"
                ))
            }
        }
    }

    /**
     * Register all currently active manifest/dynamic content scripts on a WebView.
     * This method is idempotent for an already-registered WebView. Use
     * refreshPersistentScriptsForWebView() when the extension set changes.
     */
    fun registerPersistentScriptsForWebView(webView: android.webkit.WebView) {
        val hash = webView.hashCode()
        if (webViewScriptHandlers.containsKey(hash)) return
        registerPersistentScriptsForWebViewInternal(webView, hash)
    }

    /**
     * Replace the native ScriptHandlers for a WebView with the current active
     * extension set. This is required after install/enable/disable/uninstall
     * so future navigations always see the latest extension registry.
     */
    fun refreshPersistentScriptsForWebView(webView: android.webkit.WebView) {
        val hash = webView.hashCode()
        val refresh = {
            webViewScriptHandlers.remove(hash)?.forEach { handler ->
                try { handler.remove() } catch (_: Throwable) {}
            }
            if (webView.isAttachedToWindow) {
                registerPersistentScriptsForWebViewInternal(webView, hash)
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            refresh()
        } else {
            webView.post(refresh)
        }
    }

    private fun registerPersistentScriptsForWebViewInternal(
        webView: android.webkit.WebView,
        hash: Int
    ) {
        val handlers = mutableListOf<androidx.webkit.ScriptHandler>()
        val activeDefinitions = getActiveDefinitions()

        for (def in activeDefinitions) {
            val isMain = def.world.uppercase().trim() == "MAIN"
            val runAtLower = def.runAt.lowercase().trim()
            val matches = def.matches
            val originRules = getOriginRules(matches)

            val jsContentBuilder = StringBuilder()
            
            for (jsPath in def.jsFiles) {
                try {
                    val fileContent = readExtensionFile(
                        extensionId = def.extensionId,
                        relativePath = jsPath,
                        isPrivate = false,
                        webViewHash = hash,
                        url = "about:blank",
                        frameId = 0,
                        scriptId = def.scriptId
                    )
                    if (fileContent.isNotBlank()) {
                        jsContentBuilder.append(fileContent).append("\n")
                    }
                } catch (e: Exception) {}
            }
            
            val compiledJs = jsContentBuilder.toString()
            if (compiledJs.isBlank()) continue

            var wrappedJs = compiledJs
            if (!def.allFrames) {
                wrappedJs = "if (window.top === window.self) {\n$wrappedJs\n}"
            }

            try {
                if (runAtLower == "document_start") {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
                        val worldObj = if (isMain) {
                            JavaScriptExecutionWorld(JavaScriptExecutionWorld.PAGE_WORLD_NAME, webView)
                        } else {
                            JavaScriptExecutionWorld("isolated_world_${def.extensionId}", webView)
                        }
                        val handler = WebViewCompat.addJavaScriptOnEvent(
                            webView,
                            wrappedJs,
                            WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                            originRules,
                            worldObj
                        )
                        handlers.add(handler)
                    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        // Older WebView: document-start is still native, but cannot select an execution world.
                        val handler = WebViewCompat.addDocumentStartJavaScript(
                            webView,
                            wrappedJs,
                            originRules
                        )
                        handlers.add(handler)
                    }
                } else if (runAtLower == "document_end" || runAtLower == "document_idle") {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
                        val worldObj = if (isMain) {
                            JavaScriptExecutionWorld(JavaScriptExecutionWorld.PAGE_WORLD_NAME, webView)
                        } else {
                            JavaScriptExecutionWorld("isolated_world_${def.extensionId}", webView)
                        }

                        val handler = WebViewCompat.addJavaScriptOnEvent(
                            webView,
                            wrappedJs,
                            WebViewCompat.INJECTION_EVENT_DOCUMENT_END,
                            originRules,
                            worldObj
                        )
                        handlers.add(handler)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        
        webViewScriptHandlers[hash] = handlers
    }

    fun convertPatternToOriginRule(pattern: String): String {
        val clean = pattern.trim()
        if (clean == "<all_urls>" || clean == "*" || clean == "*://*/*") return "*"

        // WebView allowedOriginRules do not accept a scheme wildcard.
        // A Chrome pattern such as *://*.example.com/* therefore becomes
        // two explicit origin rules. getOriginRules() handles this expansion.
        val withoutPath = clean.substringBefore('/', clean).trimEnd('/')
        val scheme = withoutPath.substringBefore("://", "").lowercase()
        val authority = withoutPath.substringAfter("://", "")
        if (scheme !in setOf("http", "https")) {
            val custom = scheme.ifBlank { clean.substringBefore(":", "") }
            return if (custom.isNotBlank()) "$custom://" else "*"
        }

        val hostPort = authority.substringBefore('/')
        val host = hostPort.substringBefore(':').trim()
        val port = hostPort.substringAfter(':', "")
        if (host.isBlank()) return "*"

        val normalizedHost = when {
            host == "*" -> "*"
            host.startsWith("*.") -> host
            else -> host.lowercase()
        }
        return buildString {
            append(scheme)
            append("://")
            append(normalizedHost)
            if (port.isNotBlank()) append(":").append(port)
        }
    }

    fun getOriginRules(matches: List<String>): Set<String> {
        val rules = linkedSetOf<String>()
        for (pattern in matches) {
            val clean = pattern.trim()
            if (clean == "<all_urls>" || clean == "*" || clean == "*://*/*") {
                rules.clear()
                rules.add("*")
                break
            }
            if (clean.startsWith("*://")) {
                val withoutPath = clean.substringBefore('/', clean).trimEnd('/')
                val authority = withoutPath.removePrefix("*://")
                val hostPort = authority.substringBefore('/')
                val host = hostPort.substringBefore(':')
                val port = hostPort.substringAfter(':', "")
                if (host.isBlank()) {
                    rules.add("*")
                    continue
                }
                val normalizedHost = host.lowercase()
                rules.add("https://$normalizedHost" + if (port.isNotBlank()) ":$port" else "")
                rules.add("http://$normalizedHost" + if (port.isNotBlank()) ":$port" else "")
            } else {
                rules.add(convertPatternToOriginRule(clean))
            }
        }
        return if (rules.isEmpty()) setOf("*") else rules
    }

    private fun readExtensionFile(
        extensionId: String,
        relativePath: String,
        isPrivate: Boolean,
        webViewHash: Int,
        url: String,
        frameId: Int,
        scriptId: String
    ): String {
        try {
            val resourceUrl = "chrome-extension://$extensionId/${relativePath.removePrefix("./").removePrefix("/")}"
            val result = resourceResolver.resolveResource(
                requestUrlStr = resourceUrl,
                initiatorUrlStr = url,
                isPrivate = isPrivate
            )
            
            val extLower = relativePath.substringAfterLast(".").lowercase()
            if (extLower != "js" && extLower != "mjs" && extLower != "css") {
                reportError(ContentScriptErrorReport(
                    extensionId = extensionId,
                    scriptId = scriptId,
                    errorType = "CONTENT_SCRIPT_RESOURCE_NOT_FOUND",
                    errorMsg = "Resource is not JS/CSS: $relativePath",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = webViewHash,
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "file_resolution"
                ))
                return ""
            }

            val content = result.inputStreamProvider?.invoke()?.bufferedReader()?.use { it.readText() } ?: ""
            if (content.isBlank()) {
                reportError(ContentScriptErrorReport(
                    extensionId = extensionId,
                    scriptId = scriptId,
                    errorType = "CONTENT_SCRIPT_RESOURCE_NOT_FOUND",
                    errorMsg = "Resolved resource is empty: $relativePath",
                    timestamp = System.currentTimeMillis(),
                    webViewInstanceId = webViewHash,
                    uri = url,
                    targetFrameId = frameId,
                    failedStep = "file_resolution"
                ))
                return ""
            }
            return content
        } catch (e: com.swift.browser.extensionengine.ExtensionError.SecurityError.ExtensionNotFound) {
            reportError(ContentScriptErrorReport(
                extensionId = extensionId,
                scriptId = scriptId,
                errorType = "CONTENT_SCRIPT_DISABLED",
                errorMsg = e.message ?: "Extension not found",
                timestamp = System.currentTimeMillis(),
                webViewInstanceId = webViewHash,
                uri = url,
                targetFrameId = frameId,
                failedStep = "file_resolution"
            ))
        } catch (e: com.swift.browser.extensionengine.ExtensionError.SecurityError.AccessDenied) {
            val errType = if (isPrivate) "CONTENT_SCRIPT_PRIVATE_BLOCKED" else "CONTENT_SCRIPT_DISABLED"
            reportError(ContentScriptErrorReport(
                extensionId = extensionId,
                scriptId = scriptId,
                errorType = errType,
                errorMsg = e.message ?: "Access Denied",
                timestamp = System.currentTimeMillis(),
                webViewInstanceId = webViewHash,
                uri = url,
                targetFrameId = frameId,
                failedStep = "file_resolution"
            ))
        } catch (e: com.swift.browser.extensionengine.ExtensionError.SecurityError.ResourceNotAccessible) {
            reportError(ContentScriptErrorReport(
                extensionId = extensionId,
                scriptId = scriptId,
                errorType = "CONTENT_SCRIPT_RESOURCE_NOT_FOUND",
                errorMsg = e.message ?: "Resource not found on disk",
                timestamp = System.currentTimeMillis(),
                webViewInstanceId = webViewHash,
                uri = url,
                targetFrameId = frameId,
                failedStep = "file_resolution"
            ))
        } catch (e: com.swift.browser.extensionengine.ExtensionError.SecurityError.PathTraversalDetected) {
            reportError(ContentScriptErrorReport(
                extensionId = extensionId,
                scriptId = scriptId,
                errorType = "CONTENT_SCRIPT_RESOURCE_NOT_FOUND",
                errorMsg = e.message ?: "Path Traversal Detected",
                timestamp = System.currentTimeMillis(),
                webViewInstanceId = webViewHash,
                uri = url,
                targetFrameId = frameId,
                failedStep = "file_resolution"
            ))
        } catch (e: Exception) {
            reportError(ContentScriptErrorReport(
                extensionId = extensionId,
                scriptId = scriptId,
                errorType = "CONTENT_SCRIPT_RESOURCE_NOT_FOUND",
                errorMsg = e.message ?: "File resolution failed",
                timestamp = System.currentTimeMillis(),
                webViewInstanceId = webViewHash,
                uri = url,
                targetFrameId = frameId,
                failedStep = "file_resolution"
            ))
        }
        return ""
    }
}
