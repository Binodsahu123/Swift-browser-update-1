package com.swift.browser.extensionengine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ExtensionScriptingAdapter(
    private val permissionAdapter: ExtensionPermissionAdapter,
    private val registry: ExtensionRegistry,
    private val contentScriptManager: ContentScriptManager,
    private val scriptInjector: ScriptInjector = ScriptInjector(),
    private val cssInjector: CssInjector = CssInjector()
) {

    constructor(
        permissionManager: PermissionManager,
        registry: ExtensionRegistry,
        contentScriptManager: ContentScriptManager
    ) : this(
        permissionAdapter = ExtensionPermissionAdapter(permissionManager.context).also { it.setRegistry(registry) },
        registry = registry,
        contentScriptManager = contentScriptManager
    )

    companion object {
        val tabDocumentIds = ConcurrentHashMap<String, String>() // tabId -> documentId
        val tabGenerations = ConcurrentHashMap<String, Int>()    // tabId -> generationInt
        val tabLastUrls = ConcurrentHashMap<String, String>()    // tabId -> lastTestedUrl
        val tabInsertedStyles = ConcurrentHashMap<String, MutableSet<String>>() // tabId -> styleKeys
    }

    private fun getCanonicalDocumentId(tabId: String, currentUrl: String): String {
        val gen = tabGenerations.getOrPut(tabId) { 1 }
        val lastUrl = tabLastUrls[tabId]
        val updatedGen = if (lastUrl != null && lastUrl != currentUrl) {
            tabLastUrls[tabId] = currentUrl
            val nextGen = gen + 1
            tabGenerations[tabId] = nextGen
            nextGen
        } else {
            tabLastUrls[tabId] = currentUrl
            gen
        }
        val docId = "doc_${tabId}_${updatedGen}"
        tabDocumentIds[tabId] = docId
        return docId
    }

    fun executeScript(
        sender: ExtensionSender,
        spec: JSONObject,
        delegate: BrowserDelegate?,
        context: Context,
        callback: (JSONArray?, String?) -> Unit
    ) {
        val extId = sender.extensionId

        val target = spec.optJSONObject("target")
        if (target == null) {
            callback(null, "SCRIPTING_INVALID_TARGET")
            return
        }
        val tabIdObj = target.opt("tabId")
        if (tabIdObj == null) {
            callback(null, "SCRIPTING_INVALID_TARGET")
            return
        }
        val tabIdRaw = tabIdObj.toString()
        if (tabIdRaw.isBlank()) {
            callback(null, "SCRIPTING_INVALID_TARGET")
            return
        }

        val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
        val allFrames = target.optBoolean("allFrames", false)
        val frameIdsArray = target.optJSONArray("frameIds")
        val frameIds = mutableListOf<Int>()
        if (frameIdsArray != null) {
            for (i in 0 until frameIdsArray.length()) {
                frameIds.add(frameIdsArray.getInt(i))
            }
        } else {
            frameIds.add(0)
        }

        // Frame and AllFrames capability checks
        if (allFrames || frameIds.any { it != 0 }) {
            val primaryFrame = if (frameIds.isNotEmpty()) frameIds.first() else 0
            if (!OrionWebViewScriptingCapabilities.canTargetFrame(primaryFrame, allFrames)) {
                callback(null, "SCRIPTING_FRAME_TARGETING_UNSUPPORTED")
                return
            }
        }

        val world = spec.optString("world", "ISOLATED").uppercase()
        if (spec.has("world")) {
            if (!OrionWebViewScriptingCapabilities.canTargetWorld(world)) {
                callback(null, "SCRIPTING_WORLD_UNSUPPORTED")
                return
            }
        }

        val allTabs = delegate?.queryTabs(JSONObject()) ?: JSONArray()
        var targetUrl: String? = null
        for (i in 0 until allTabs.length()) {
            val t = allTabs.optJSONObject(i)
            if (t != null) {
                val idVal = t.opt("id")?.toString()
                if (idVal == tabIdRaw || idVal == resolvedTabId) {
                    targetUrl = t.optString("url", "")
                    break
                }
            }
        }

        if (targetUrl == null) {
            callback(null, "SCRIPTING_TAB_NOT_FOUND")
            return
        }

        // Validate permissions via canonical permissionAdapter
        val validationErr = checkScriptingValidation(sender, targetUrl)
        if (validationErr != null) {
            callback(null, validationErr)
            return
        }

        // Canonical document identity & stale checks
        val currentDocId = getCanonicalDocumentId(resolvedTabId.ifBlank { tabIdRaw }, targetUrl)
        val targetDocIds = target.optJSONArray("documentIds")
        if (targetDocIds != null && targetDocIds.length() > 0) {
            var matched = false
            for (i in 0 until targetDocIds.length()) {
                if (targetDocIds.getString(i) == currentDocId) {
                    matched = true
                    break
                }
            }
            if (!matched) {
                callback(null, "SCRIPTING_DOCUMENT_STALE")
                return
            }
        }

        val funcCode = spec.optString("func", "")
        val files = spec.optJSONArray("files")
        val args = spec.optJSONArray("args") ?: JSONArray()

        val argErr = validateJsonArgs(args)
        if (argErr != null) {
            callback(null, argErr)
            return
        }

        val codeBuilder = StringBuilder()
        if (funcCode.isNotBlank()) {
            val trimmed = funcCode.trim()
            val isValidFunc = trimmed.startsWith("function") || trimmed.contains("=>") || (trimmed.startsWith("(") && trimmed.endsWith(")"))
            if (!isValidFunc) {
                callback(null, "SCRIPTING_FUNCTION_UNSUPPORTED")
                return
            }

            val argsStr = args.toString()
            codeBuilder.append("""
                (function() {
                    try {
                        const __fn = ($funcCode);
                        const __args = $argsStr;
                        const __res = __fn.apply(null, __args);
                        if (__res instanceof Promise) {
                            return JSON.stringify({ status: "unserializable" });
                        }
                        if (typeof __res === 'function' || typeof __res === 'symbol') {
                            return JSON.stringify({ status: "unserializable" });
                        }
                        if (__res && typeof __res === 'object' && ('nodeType' in __res)) {
                            return JSON.stringify({ status: "unserializable" });
                        }
                        try {
                            const __json = JSON.stringify(__res);
                            if (__json === undefined) {
                                return JSON.stringify({ status: "unserializable" });
                            }
                            JSON.parse(__json);
                            return JSON.stringify({ status: "success", result: __res !== undefined ? __res : null });
                        } catch(e) {
                            return JSON.stringify({ status: "unserializable" });
                        }
                    } catch (__e) {
                        return JSON.stringify({ status: "error", error: __e.message || String(__e) });
                    }
                })()
            """.trimIndent())
        } else if (files != null && files.length() > 0) {
            val fileContents = StringBuilder()
            for (i in 0 until files.length()) {
                val path = files.optString(i, "")
                if (path.isNotBlank()) {
                    try {
                        val content = readExtensionFile(context, extId, path, sender.isPrivate)
                        if (content.isBlank()) {
                            callback(null, "SCRIPTING_RESOURCE_NOT_FOUND")
                            return
                        }
                        fileContents.append(content).append("\n")
                    } catch (e: Exception) {
                        callback(null, "SCRIPTING_RESOURCE_NOT_FOUND")
                        return
                    }
                }
            }
            val fileCode = fileContents.toString()
            codeBuilder.append("""
                (function() {
                    try {
                        const __res = (function() { $fileCode })();
                        if (__res instanceof Promise) {
                            return JSON.stringify({ status: "unserializable" });
                        }
                        if (typeof __res === 'function' || typeof __res === 'symbol') {
                            return JSON.stringify({ status: "unserializable" });
                        }
                        if (__res && typeof __res === 'object' && ('nodeType' in __res)) {
                            return JSON.stringify({ status: "unserializable" });
                        }
                        try {
                            const __json = JSON.stringify(__res);
                            if (__json === undefined) {
                                return JSON.stringify({ status: "unserializable" });
                            }
                            JSON.parse(__json);
                            return JSON.stringify({ status: "success", result: __res !== undefined ? __res : null });
                        } catch(e) {
                            return JSON.stringify({ status: "unserializable" });
                        }
                    } catch (__e) {
                        return JSON.stringify({ status: "error", error: __e.message || String(__e) });
                    }
                })()
            """.trimIndent())
        }

        val finalCode = codeBuilder.toString()
        if (finalCode.isBlank()) {
            callback(null, "SCRIPTING_INVALID_TARGET")
            return
        }

        if (delegate == null) {
            callback(null, "SCRIPTING_WEBVIEW_FEATURE_UNAVAILABLE")
            return
        }

        // Script execution routed strictly through ScriptInjector
        scriptInjector.executeScriptOnTab(delegate, resolvedTabId.ifBlank { tabIdRaw }, finalCode) { rawRes ->
            try {
                if (rawRes == null || rawRes == "null") {
                    val resultsArr = JSONArray()
                    resultsArr.put(JSONObject().apply {
                        put("frameId", 0)
                        put("documentId", currentDocId)
                        put("result", JSONObject.NULL)
                        put("success", true)
                    })
                    callback(resultsArr, null)
                    return@executeScriptOnTab
                }

                val cleanRaw = if (rawRes.startsWith("\"") && rawRes.endsWith("\"")) {
                    try { JSONObject("{ \"v\": $rawRes }").getString("v") } catch (e: Exception) { rawRes }
                } else rawRes

                val parsed = try { JSONObject(cleanRaw) } catch (e: Exception) { null }

                if (parsed != null && parsed.has("status")) {
                    val status = parsed.getString("status")
                    if (status == "success") {
                        val valRes = parsed.opt("result")
                        val resultsArr = JSONArray()
                        resultsArr.put(JSONObject().apply {
                            put("frameId", 0)
                            put("documentId", currentDocId)
                            put("result", valRes ?: JSONObject.NULL)
                            put("success", true)
                        })
                        callback(resultsArr, null)
                    } else if (status == "unserializable") {
                        callback(null, "SCRIPTING_RESULT_UNSERIALIZABLE")
                    } else {
                        val errStr = parsed.optString("error", "Script execution error")
                        callback(null, errStr)
                    }
                } else {
                    val resultsArr = JSONArray()
                    resultsArr.put(JSONObject().apply {
                        put("frameId", 0)
                        put("documentId", currentDocId)
                        put("result", rawRes)
                        put("success", true)
                    })
                    callback(resultsArr, null)
                }
            } catch (e: Exception) {
                callback(null, e.message ?: "Script evaluation failed")
            }
        }
    }

    fun insertCSS(
        sender: ExtensionSender,
        spec: JSONObject,
        delegate: BrowserDelegate?,
        context: Context
    ): JSONObject {
        val extId = sender.extensionId

        val target = spec.optJSONObject("target") ?: throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
        val tabIdRaw = target.optString("tabId", "")
        if (tabIdRaw.isBlank()) throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
        val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)

        val allTabs = delegate?.queryTabs(JSONObject()) ?: JSONArray()
        var targetUrl: String? = null
        for (i in 0 until allTabs.length()) {
            val t = allTabs.optJSONObject(i)
            if (t != null) {
                val idVal = t.opt("id")?.toString()
                if (idVal == tabIdRaw || idVal == resolvedTabId) {
                    targetUrl = t.optString("url", "")
                    break
                }
            }
        }
        if (targetUrl == null) {
            throw IllegalArgumentException("SCRIPTING_TAB_NOT_FOUND")
        }

        val err = checkScriptingValidation(sender, targetUrl)
        if (err != null) {
            throw IllegalArgumentException(err)
        }

        val css = spec.optString("css", "")
        val files = spec.optJSONArray("files")

        val cssBuilder = StringBuilder()
        if (css.isNotBlank()) {
            cssBuilder.append(css)
        } else if (files != null && files.length() > 0) {
            for (i in 0 until files.length()) {
                val path = files.optString(i, "")
                if (path.isNotBlank()) {
                    try {
                        val content = readExtensionFile(context, extId, path, sender.isPrivate)
                        if (content.isBlank()) {
                            throw IllegalArgumentException("SCRIPTING_RESOURCE_NOT_FOUND")
                        }
                        cssBuilder.append(content).append("\n")
                    } catch (e: Exception) {
                        throw IllegalArgumentException("SCRIPTING_RESOURCE_NOT_FOUND")
                    }
                }
            }
        }

        val finalCss = cssBuilder.toString()
        if (finalCss.isBlank()) throw IllegalArgumentException("SCRIPTING_CSS_UNSUPPORTED")

        val styleKey = "user_css_${extId}_${finalCss.hashCode()}"
        val activeDelegate = delegate ?: throw IllegalArgumentException("SCRIPTING_WEBVIEW_FEATURE_UNAVAILABLE")
        val evaluator = DelegateScriptEvaluator(activeDelegate, resolvedTabId.ifBlank { tabIdRaw })

        // Inject CSS via canonical CssInjector
        cssInjector.injectCssWithKey(evaluator, styleKey, finalCss)

        val set = tabInsertedStyles.getOrPut(resolvedTabId) { ConcurrentHashMap.newKeySet() }
        set.add(styleKey)

        return JSONObject().put("status", "success")
    }

    fun removeCSS(
        sender: ExtensionSender,
        spec: JSONObject,
        delegate: BrowserDelegate?,
        context: Context
    ): JSONObject {
        val extId = sender.extensionId

        val target = spec.optJSONObject("target") ?: throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
        val tabIdRaw = target.optString("tabId", "")
        if (tabIdRaw.isBlank()) throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
        val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)

        val allTabs = delegate?.queryTabs(JSONObject()) ?: JSONArray()
        var targetUrl: String? = null
        for (i in 0 until allTabs.length()) {
            val t = allTabs.optJSONObject(i)
            if (t != null) {
                val idVal = t.opt("id")?.toString()
                if (idVal == tabIdRaw || idVal == resolvedTabId) {
                    targetUrl = t.optString("url", "")
                    break
                }
            }
        }
        if (targetUrl == null) {
            throw IllegalArgumentException("SCRIPTING_TAB_NOT_FOUND")
        }

        val err = checkScriptingValidation(sender, targetUrl)
        if (err != null) {
            throw IllegalArgumentException(err)
        }

        val css = spec.optString("css", "")
        val files = spec.optJSONArray("files")

        val cssBuilder = StringBuilder()
        if (css.isNotBlank()) {
            cssBuilder.append(css)
        } else if (files != null && files.length() > 0) {
            for (i in 0 until files.length()) {
                val path = files.optString(i, "")
                if (path.isNotBlank()) {
                    try {
                        val content = readExtensionFile(context, extId, path, sender.isPrivate)
                        cssBuilder.append(content).append("\n")
                    } catch (e: Exception) {
                        throw IllegalArgumentException("SCRIPTING_RESOURCE_NOT_FOUND")
                    }
                }
            }
        }

        val finalCss = cssBuilder.toString()
        val styleKey = "user_css_${extId}_${finalCss.hashCode()}"

        val set = tabInsertedStyles[resolvedTabId]
        if (set == null || !set.contains(styleKey)) {
            throw IllegalArgumentException("SCRIPTING_REMOVE_CSS_PARTIAL")
        }

        val activeDelegate = delegate ?: throw IllegalArgumentException("SCRIPTING_WEBVIEW_FEATURE_UNAVAILABLE")
        val evaluator = DelegateScriptEvaluator(activeDelegate, resolvedTabId.ifBlank { tabIdRaw })

        // Remove CSS via canonical CssInjector
        cssInjector.removeCssByKey(evaluator, styleKey)
        set.remove(styleKey)

        return JSONObject().put("status", "success")
    }

    fun registerContentScripts(sender: ExtensionSender, scriptsArray: JSONArray): JSONObject {
        val extId = sender.extensionId
        validateScriptingPermission(sender)

        val specs = mutableListOf<DynamicContentScriptSpec>()
        val existing = contentScriptManager.getRegisteredContentScripts(extId)
        val existingIds = existing.map { it.id }.toSet()

        for (i in 0 until scriptsArray.length()) {
            val obj = scriptsArray.getJSONObject(i)
            val id = obj.getString("id")
            if (existingIds.contains(id)) {
                throw IllegalArgumentException("SCRIPTING_DUPLICATE_REGISTRATION")
            }
            val matches = parseStringList(obj.optJSONArray("matches"))
            val js = parseStringList(obj.optJSONArray("js"))
            val css = parseStringList(obj.optJSONArray("css"))
            val runAt = obj.optString("runAt", "document_idle")
            val allFrames = obj.optBoolean("allFrames", false)
            val world = obj.optString("world", "ISOLATED")

            val spec = DynamicContentScriptSpec(id, matches, js, css, runAt, allFrames, world)
            validateDynamicScriptSpec(sender, spec)
            specs.add(spec)
        }

        contentScriptManager.registerContentScripts(extId, specs)
        return JSONObject().put("status", "success")
    }

    fun unregisterContentScripts(sender: ExtensionSender, idsArray: JSONArray?): JSONObject {
        val extId = sender.extensionId
        validateScriptingPermission(sender)

        val ids = parseStringList(idsArray)
        if (ids.isNotEmpty()) {
            val existing = contentScriptManager.getRegisteredContentScripts(extId)
            val existingIds = existing.map { it.id }.toSet()
            for (id in ids) {
                if (!existingIds.contains(id)) {
                    throw IllegalArgumentException("SCRIPTING_REGISTRATION_NOT_FOUND")
                }
            }
        }

        contentScriptManager.unregisterContentScripts(extId, if (ids.isEmpty()) null else ids)
        return JSONObject().put("status", "success")
    }

    fun updateContentScripts(sender: ExtensionSender, scriptsArray: JSONArray): JSONObject {
        val extId = sender.extensionId
        validateScriptingPermission(sender)

        val specs = mutableListOf<DynamicContentScriptSpec>()
        val existing = contentScriptManager.getRegisteredContentScripts(extId)
        val existingIds = existing.map { it.id }.toSet()

        for (i in 0 until scriptsArray.length()) {
            val obj = scriptsArray.getJSONObject(i)
            val id = obj.getString("id")
            if (!existingIds.contains(id)) {
                throw IllegalArgumentException("SCRIPTING_REGISTRATION_NOT_FOUND")
            }
            val matches = parseStringList(obj.optJSONArray("matches"))
            val js = parseStringList(obj.optJSONArray("js"))
            val css = parseStringList(obj.optJSONArray("css"))
            val runAt = obj.optString("runAt", "document_idle")
            val allFrames = obj.optBoolean("allFrames", false)
            val world = obj.optString("world", "ISOLATED")

            val spec = DynamicContentScriptSpec(id, matches, js, css, runAt, allFrames, world)
            validateDynamicScriptSpec(sender, spec)
            specs.add(spec)
        }

        contentScriptManager.updateContentScripts(extId, specs)
        return JSONObject().put("status", "success")
    }

    fun getRegisteredContentScripts(sender: ExtensionSender): JSONArray {
        validateScriptingPermission(sender)
        val list = contentScriptManager.getRegisteredContentScripts(sender.extensionId)
        val array = JSONArray()
        for (s in list) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("matches", JSONArray(s.matches))
                put("js", JSONArray(s.js))
                put("css", JSONArray(s.css))
                put("runAt", s.runAt)
                put("allFrames", s.allFrames)
                put("world", s.world)
            }
            array.put(obj)
        }
        return array
    }

    private fun validateScriptingPermission(sender: ExtensionSender) {
        val extId = sender.extensionId
        val ext = registry.getExtension(extId) ?: throw SecurityException("SCRIPTING_EXTENSION_DISABLED")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("SCRIPTING_EXTENSION_DISABLED")
        }
        if (sender.isPrivate && !permissionAdapter.isAllowedInPrivate(extId)) {
            throw SecurityException("SCRIPTING_PRIVATE_MODE_DENIED")
        }
        val hasPerm = ext.permissions.contains("scripting") || permissionAdapter.hasApiPermission(extId, "scripting", sender.isPrivate)
        if (!hasPerm) {
            throw SecurityException("SCRIPTING_PERMISSION_DENIED")
        }
    }

    private fun checkScriptingValidation(sender: ExtensionSender, targetUrl: String): String? {
        val extId = sender.extensionId
        val ext = registry.getExtension(extId) ?: return "SecurityError: SCRIPTING_EXTENSION_DISABLED"
        if (!registry.isExtensionEnabled(extId)) {
            return "SecurityError: SCRIPTING_EXTENSION_DISABLED"
        }
        if (sender.isPrivate && !permissionAdapter.isAllowedInPrivate(extId)) {
            return "SecurityError: SCRIPTING_PRIVATE_MODE_DENIED"
        }
        val hasPerm = ext.permissions.contains("scripting") || permissionAdapter.hasApiPermission(extId, "scripting", sender.isPrivate)
        if (!hasPerm) {
            return "SecurityError: SCRIPTING_PERMISSION_DENIED"
        }
        if (targetUrl.startsWith("swift:") || targetUrl.startsWith("chrome://") || targetUrl.startsWith("file://")) {
            return "SecurityError: SCRIPTING_HOST_DENIED"
        }
        val hasHostPermission = permissionAdapter.hasHostPermission(extId, targetUrl, sender.isPrivate) ||
                permissionAdapter.contains(extId, permissions = listOf("activeTab"), origins = emptyList(), isPrivate = sender.isPrivate) ||
                ext.permissions.contains("activeTab")
        if (!hasHostPermission) {
            return "SecurityError: SCRIPTING_HOST_DENIED"
        }
        return null
    }

    private fun validateDynamicScriptSpec(sender: ExtensionSender, spec: DynamicContentScriptSpec) {
        val extId = sender.extensionId
        val ext = registry.getExtension(extId) ?: throw IllegalArgumentException("SCRIPTING_EXTENSION_DISABLED")

        if (spec.id.isBlank()) {
            throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
        }
        if (spec.matches.isEmpty()) {
            throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
        }
        for (pattern in spec.matches) {
            try {
                ExtensionMatchPattern(pattern)
            } catch (e: Exception) {
                throw IllegalArgumentException("SCRIPTING_INVALID_TARGET")
            }
        }
        for (path in spec.js) {
            if (path.contains("..") || path.startsWith("/")) {
                throw IllegalArgumentException("SCRIPTING_RESOURCE_UNSAFE")
            }
        }
        for (path in spec.css) {
            if (path.contains("..") || path.startsWith("/")) {
                throw IllegalArgumentException("SCRIPTING_RESOURCE_UNSAFE")
            }
        }
        val runAtLower = spec.runAt.lowercase().trim()
        if (runAtLower != "document_start" && runAtLower != "document_end" && runAtLower != "document_idle") {
            throw IllegalArgumentException("SCRIPTING_CSS_UNSUPPORTED")
        }
        val worldUpper = spec.world.uppercase().trim()
        if (worldUpper != "MAIN" && worldUpper != "ISOLATED") {
            throw IllegalArgumentException("SCRIPTING_WORLD_UNSUPPORTED")
        }
    }

    private fun readExtensionFile(context: Context, extensionId: String, path: String, isPrivate: Boolean): String {
        val resolver = com.swift.browser.extensionengine.resources.ExtensionResourceResolver(context, registry, PermissionManager(context))
        val resourceUrl = "chrome-extension://$extensionId/${path.removePrefix("./").removePrefix("/")}"
        if (path.contains("..")) {
            throw SecurityException("SCRIPTING_RESOURCE_UNSAFE")
        }
        val result = resolver.resolveResource(
            requestUrlStr = resourceUrl,
            initiatorUrlStr = "about:blank",
            isPrivate = isPrivate
        )
        return result.inputStreamProvider?.invoke()?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun validateJsonArgs(args: JSONArray?): String? {
        if (args == null) return null
        for (i in 0 until args.length()) {
            val item = args.opt(i) ?: continue
            val err = validateJsonValue(item)
            if (err != null) return err
        }
        return null
    }

    private fun validateJsonValue(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        if (value is Boolean || value is Number || value is String) return null
        if (value is JSONObject) {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val err = validateJsonValue(value.opt(key))
                if (err != null) return err
            }
            return null
        }
        if (value is JSONArray) {
            for (i in 0 until value.length()) {
                val err = validateJsonValue(value.opt(i))
                if (err != null) return err
            }
            return null
        }
        return "SCRIPTING_ARGUMENT_UNSUPPORTED"
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }
}
