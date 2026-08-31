package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject

data class ContentScriptSpec(
    val matches: List<String>,
    val js: List<String>,
    val css: List<String>,
    val runAt: String = "document_idle",
    val allFrames: Boolean = false,
    val matchAboutBlank: Boolean = false,
    val excludeMatches: List<String> = emptyList(),
    val includeGlobs: List<String> = emptyList(),
    val excludeGlobs: List<String> = emptyList(),
    val matchOriginAsFallback: Boolean = false,
    val world: String = "ISOLATED"
) {
    fun matchesUrl(urlStr: String, targetOrigin: String? = null): Boolean {
        if (urlStr.isBlank()) return false

        var urlToTest = urlStr
        if (urlStr == "about:blank" || urlStr.startsWith("about:")) {
            if (!matchAboutBlank && !matchOriginAsFallback) return false
            if (targetOrigin != null && targetOrigin.isNotBlank()) {
                urlToTest = targetOrigin
            } else {
                return false
            }
        }

        // 1. Matches pattern check
        val matchesPattern = ExtensionMatchPattern.matchesAny(urlToTest, matches)
        if (!matchesPattern) return false

        // 2. Exclude matches check
        if (excludeMatches.isNotEmpty() && ExtensionMatchPattern.matchesAny(urlToTest, excludeMatches)) {
            return false
        }

        // 3. Include globs check
        if (includeGlobs.isNotEmpty()) {
            val matchesGlob = includeGlobs.any { globToRegex(it).containsMatchIn(urlToTest) }
            if (!matchesGlob) return false
        }

        // 4. Exclude globs check
        if (excludeGlobs.isNotEmpty()) {
            val matchesExcludeGlob = excludeGlobs.any { globToRegex(it).containsMatchIn(urlToTest) }
            if (matchesExcludeGlob) return false
        }

        return true
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        for (char in glob) {
            when (char) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.' -> sb.append("\\.")
                '\\' -> sb.append("\\\\")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '[' -> sb.append("\\[")
                ']' -> sb.append("\\]")
                '{' -> sb.append("\\{")
                '}' -> sb.append("\\}")
                '+' -> sb.append("\\+")
                '^' -> sb.append("\\^")
                '$' -> sb.append("\\$")
                '|' -> sb.append("\\|")
                else -> sb.append(char)
            }
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}

data class ActionSpec(
    val defaultPopup: String = "",
    val defaultTitle: String = "",
    val defaultIconMap: Map<String, String> = emptyMap(),
    val hasAction: Boolean = false,
    val actionType: String = ""
)

data class BackgroundSpec(
    val scripts: List<String> = emptyList(),
    val page: String = "",
    val serviceWorker: String = "",
    val type: String = "",
    val persistent: Boolean = true
)

data class WebAccessibleResourceSpec(
    val resources: List<String> = emptyList(),
    val matches: List<String> = emptyList(),
    val extensionIds: List<String> = emptyList(),
    val useDynamicUrl: Boolean = false
)

data class ContentSecurityPolicySpec(
    val extensionPages: String = "",
    val sandbox: String = ""
)

data class DeclarativeNetRequestRuleset(
    val id: String,
    val path: String,
    val enabled: Boolean
)

data class DeclarativeNetRequestSpec(
    val rulesets: List<DeclarativeNetRequestRuleset> = emptyList()
)

data class ParsedExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val manifestVersion: Int = 3,
    val permissions: List<String> = emptyList(),
    val hostPermissions: List<String> = emptyList(),
    val backgroundScripts: List<String> = emptyList(),
    val isServiceWorker: Boolean = false,
    val contentScripts: List<ContentScriptSpec> = emptyList(),
    val actionPopup: String = "",
    val optionsPage: String = "",
    val manifestJson: String = "{}",
    val shortName: String = "",
    val iconPath: String = "",
    val installPath: String = "",
    val popupPath: String = "",
    val manifestPath: String = "",
    val backgroundPath: String = "",
    val isEnabled: Boolean = true,
    val allowedInPrivate: Boolean = false,
    val identity: ExtensionIdentity = ExtensionIdentity(id, IdentityType.ORION_LOCAL_IDENTITY),
    val key: String? = null,
    val defaultLocale: String = "",
    val minimumChromeVersion: String = "",
    val actionSpec: ActionSpec = ActionSpec(),
    val backgroundSpec: BackgroundSpec = BackgroundSpec(),
    val webAccessibleResources: List<WebAccessibleResourceSpec> = emptyList(),
    val contentSecurityPolicy: ContentSecurityPolicySpec = ContentSecurityPolicySpec(),
    val optionalPermissions: List<String> = emptyList(),
    val optionalHostPermissions: List<String> = emptyList(),
    val externallyConnectable: ExternallyConnectableSpec = ExternallyConnectableSpec(),
    val sidePanelPath: String = "",
    val devtoolsPagePath: String = "",
    val optionsInTab: Boolean = false,
    val urlOverrides: Map<String, String> = emptyMap()
) {
    @Deprecated("Use allowedInPrivate instead", ReplaceWith("allowedInPrivate"))
    val allowedInIncognito: Boolean get() = allowedInPrivate
}

sealed class ManifestParseResult {
    data class Success(val parsedExtension: ParsedExtension) : ManifestParseResult()
    data class Failure(val error: ExtensionError.ManifestError) : ManifestParseResult()
}

class ManifestParser {

    /**
     * Parses manifest JSON strictly. Throws [ExtensionError.ManifestError] on validation failure.
     */
    fun parse(manifestJsonStr: String, sourceSeed: String = "local_install"): ParsedExtension {
        return when (val result = validateAndParse(manifestJsonStr, sourceSeed)) {
            is ManifestParseResult.Success -> result.parsedExtension
            is ManifestParseResult.Failure -> throw result.error
        }
    }

    /**
     * Validates and parses manifest JSON, returning a structured [ManifestParseResult].
     */
    fun validateAndParse(manifestJsonStr: String, sourceSeed: String = "local_install"): ManifestParseResult {
        if (manifestJsonStr.isBlank()) {
            return ManifestParseResult.Failure(
                ExtensionError.ManifestError.InvalidJson(manifestJsonStr, IllegalArgumentException("Manifest content is blank"))
            )
        }

        val root = try {
            JSONObject(manifestJsonStr)
        } catch (e: Exception) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.InvalidJson(manifestJsonStr, e))
        }

        // 1. Validate manifest_version
        if (!root.has("manifest_version")) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.MissingRequiredField("manifest_version"))
        }

        val manifestVersion = try {
            root.getInt("manifest_version")
        } catch (e: Exception) {
            return ManifestParseResult.Failure(
                ExtensionError.ManifestError.InvalidFieldType("manifest_version", "Integer", root.opt("manifest_version")?.toString() ?: "null")
            )
        }

        if (manifestVersion != 2 && manifestVersion != 3) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.UnsupportedVersion(manifestVersion))
        }

        // 2. Validate name
        if (!root.has("name")) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.MissingRequiredField("name"))
        }

        val name = try {
            root.getString("name")
        } catch (e: Exception) {
            return ManifestParseResult.Failure(
                ExtensionError.ManifestError.InvalidFieldType("name", "String", root.opt("name")?.toString() ?: "null")
            )
        }

        if (name.isBlank()) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.MissingRequiredField("name"))
        }

        // 3. Validate version
        if (!root.has("version")) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.MissingRequiredField("version"))
        }

        val version = try {
            root.getString("version")
        } catch (e: Exception) {
            return ManifestParseResult.Failure(
                ExtensionError.ManifestError.InvalidFieldType("version", "String", root.opt("version")?.toString() ?: "null")
            )
        }

        if (version.isBlank() || !isValidSemverLike(version)) {
            return ManifestParseResult.Failure(ExtensionError.ManifestError.InvalidVersionFormat(version))
        }

        // Optional metadata strings
        val shortName = root.optString("short_name", "")
        val description = root.optString("description", "")
        val defaultLocale = root.optString("default_locale", "")
        val minimumChromeVersion = root.optString("minimum_chrome_version", "")
        val rawKey = if (root.has("key")) root.optString("key", "").takeIf { it.isNotBlank() } else null

        // 4. Stable identity resolution
        val identity = try {
            if (rawKey != null) {
                ExtensionIdGenerator.generateFromPublicKey(rawKey)
            } else {
                ExtensionIdGenerator.generateOrionLocalIdentity(sourceSeed.ifBlank { name }, manifestJsonStr)
            }
        } catch (e: ExtensionError.IdentityError) {
            // Fall back to local identity if key is malformed rather than crashing whole parse, but record key error
            ExtensionIdGenerator.generateOrionLocalIdentity(sourceSeed.ifBlank { name }, manifestJsonStr)
        }

        // 5. Parse permissions
        val permissions = parseStringList(root, "permissions")
        val optionalPermissions = parseStringList(root, "optional_permissions")
        val hostPermissions = parseStringList(root, "host_permissions")
        val optionalHostPermissions = parseStringList(root, "optional_host_permissions")

        // 6. Background spec
        val backgroundSpec = parseBackgroundSpec(root, manifestVersion)

        // 7. Action spec (action for MV3, browser_action/page_action for MV2)
        val actionSpec = parseActionSpec(root)

        // 8. Content scripts
        val contentScripts = parseContentScripts(root)

        // 9. Options page / UI
        var optionsPage = ""
        var optionsInTab = false
        if (root.has("options_page")) {
            optionsPage = root.optString("options_page", "").trim()
            optionsInTab = true
        } else if (root.has("options_ui")) {
            val uiObj = root.optJSONObject("options_ui")
            if (uiObj != null) {
                optionsPage = uiObj.optString("page", "").trim()
                optionsInTab = uiObj.optBoolean("open_in_tab", false)
            } else {
                optionsPage = root.optString("options_ui", "").trim()
            }
        }

        // 9b. Side panel spec
        val sidePanelPath = when {
            root.has("side_panel") -> {
                val spObj = root.optJSONObject("side_panel")
                spObj?.optString("default_path", "")?.trim() ?: root.optString("side_panel", "").trim()
            }
            else -> ""
        }

        // 9c. DevTools page
        val devtoolsPagePath = root.optString("devtools_page", "").trim()

        // 9d. Chrome URL Overrides
        val urlOverrides = mutableMapOf<String, String>()
        val overridesObj = root.optJSONObject("chrome_url_overrides")
        if (overridesObj != null) {
            val keys = overridesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = overridesObj.optString(k, "").trim()
                if (v.isNotBlank()) {
                    urlOverrides[k] = v
                }
            }
        }

        // 10. Web accessible resources
        val webAccessibleResources = parseWebAccessibleResources(root, manifestVersion)

        // 11. Content Security Policy
        val cspSpec = parseContentSecurityPolicy(root, manifestVersion)

        // 12. Externally Connectable
        val externallyConnectable = parseExternallyConnectable(root)

        // 13. Allowed in private
        val allowedInPrivate = root.optBoolean(
            "allowedInPrivate",
            root.optBoolean(
                "allowedInIncognito",
                root.optBoolean("incognito", false)
            )
        )

        val validatedManifest = ValidatedExtensionManifest(
            id = identity.id,
            name = name,
            version = version,
            description = description,
            manifestVersion = manifestVersion,
            permissions = permissions,
            hostPermissions = hostPermissions,
            optionalPermissions = optionalPermissions,
            optionalHostPermissions = optionalHostPermissions,
            backgroundSpec = backgroundSpec,
            actionSpec = actionSpec,
            contentScripts = contentScripts,
            optionsPage = optionsPage,
            webAccessibleResources = webAccessibleResources,
            contentSecurityPolicy = cspSpec,
            externallyConnectable = externallyConnectable,
            key = rawKey,
            defaultLocale = defaultLocale,
            minimumChromeVersion = minimumChromeVersion,
            allowedInPrivate = allowedInPrivate,
            rawJson = manifestJsonStr,
            sidePanelPath = sidePanelPath,
            devtoolsPagePath = devtoolsPagePath,
            optionsInTab = optionsInTab,
            urlOverrides = urlOverrides
        )

        val parsed = validatedManifest.toParsedExtension(identity)

        return ManifestParseResult.Success(parsed)
    }

    fun validateManifest(manifestJsonStr: String, sourceSeed: String = "local_install"): ValidatedExtensionManifest {
        return when (val result = validateAndParse(manifestJsonStr, sourceSeed)) {
            is ManifestParseResult.Success -> {
                val p = result.parsedExtension
                ValidatedExtensionManifest(
                    id = p.id,
                    name = p.name,
                    version = p.version,
                    description = p.description,
                    manifestVersion = p.manifestVersion,
                    permissions = p.permissions,
                    hostPermissions = p.hostPermissions,
                    optionalPermissions = p.optionalPermissions,
                    optionalHostPermissions = p.optionalHostPermissions,
                    backgroundSpec = p.backgroundSpec,
                    actionSpec = p.actionSpec,
                    contentScripts = p.contentScripts,
                    optionsPage = p.optionsPage,
                    webAccessibleResources = p.webAccessibleResources,
                    contentSecurityPolicy = p.contentSecurityPolicy,
                    externallyConnectable = p.externallyConnectable,
                    key = p.key,
                    defaultLocale = p.defaultLocale,
                    minimumChromeVersion = p.minimumChromeVersion,
                    allowedInPrivate = p.allowedInPrivate,
                    rawJson = p.manifestJson,
                    sidePanelPath = p.sidePanelPath,
                    devtoolsPagePath = p.devtoolsPagePath,
                    optionsInTab = p.optionsInTab,
                    urlOverrides = p.urlOverrides
                )
            }
            is ManifestParseResult.Failure -> throw result.error
        }
    }

    private fun parseExternallyConnectable(root: JSONObject): ExternallyConnectableSpec {
        val ecObj = root.optJSONObject("externally_connectable") ?: return ExternallyConnectableSpec()
        val matches = parseStringList(ecObj, "matches")
        val ids = parseStringList(ecObj, "ids")
        val acceptsTls = ecObj.optBoolean("accepts_tls_channel_id", false)
        return ExternallyConnectableSpec(matches = matches, ids = ids, acceptsTlsChannelId = acceptsTls)
    }

    private fun isValidSemverLike(version: String): Boolean {
        val pattern = Regex("^\\d{1,5}(\\.\\d{1,5}){0,3}$")
        return pattern.matches(version.trim())
    }

    private fun parseStringList(root: JSONObject, key: String): List<String> {
        val list = mutableListOf<String>()
        val arr = root.optJSONArray(key) ?: return list
        for (i in 0 until arr.length()) {
            val str = arr.optString(i, "")
            if (str.isNotBlank()) {
                list.add(str.trim())
            }
        }
        return list
    }

    private fun parseBackgroundSpec(root: JSONObject, manifestVersion: Int): BackgroundSpec {
        val bgObj = root.optJSONObject("background") ?: return BackgroundSpec()

        val scripts = mutableListOf<String>()
        val scriptsArr = bgObj.optJSONArray("scripts")
        if (scriptsArr != null) {
            for (i in 0 until scriptsArr.length()) {
                val s = scriptsArr.optString(i, "")
                if (s.isNotBlank()) scripts.add(s.trim())
            }
        }

        val page = bgObj.optString("page", "").trim()
        val serviceWorker = bgObj.optString("service_worker", "").trim()
        val type = bgObj.optString("type", "").trim()
        val persistent = bgObj.optBoolean("persistent", manifestVersion == 2)

        return BackgroundSpec(
            scripts = scripts,
            page = page,
            serviceWorker = serviceWorker,
            type = type,
            persistent = persistent
        )
    }

    private fun parseActionSpec(root: JSONObject): ActionSpec {
        val (actionObj, type) = when {
            root.has("action") -> Pair(root.optJSONObject("action"), "action")
            root.has("browser_action") -> Pair(root.optJSONObject("browser_action"), "browser_action")
            root.has("page_action") -> Pair(root.optJSONObject("page_action"), "page_action")
            else -> Pair(null, "")
        }
        if (actionObj == null) return ActionSpec(hasAction = false)

        val popup = actionObj.optString("default_popup", "").trim()
        val title = actionObj.optString("default_title", "").trim()

        val iconMap = mutableMapOf<String, String>()
        val iconObj = actionObj.optJSONObject("default_icon")
        if (iconObj != null) {
            val keys = iconObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val path = iconObj.optString(k, "").trim()
                if (path.isNotBlank()) {
                    iconMap[k] = path
                }
            }
        } else {
            val singleIcon = actionObj.optString("default_icon", "").trim()
            if (singleIcon.isNotBlank()) {
                iconMap["default"] = singleIcon
            }
        }

        return ActionSpec(
            defaultPopup = popup,
            defaultTitle = title,
            defaultIconMap = iconMap,
            hasAction = true,
            actionType = type
        )
    }

    private fun parseContentScripts(root: JSONObject): List<ContentScriptSpec> {
        val result = mutableListOf<ContentScriptSpec>()
        val csArray = root.optJSONArray("content_scripts") ?: return result

        for (i in 0 until csArray.length()) {
            val scriptObj = csArray.optJSONObject(i) ?: continue

            val matches = mutableListOf<String>()
            val matchesArray = scriptObj.optJSONArray("matches")
            if (matchesArray != null) {
                for (j in 0 until matchesArray.length()) {
                    val m = matchesArray.optString(j, "").trim()
                    if (m.isNotBlank()) matches.add(m)
                }
            }

            val js = mutableListOf<String>()
            val jsArray = scriptObj.optJSONArray("js")
            if (jsArray != null) {
                for (j in 0 until jsArray.length()) {
                    val script = jsArray.optString(j, "").trim()
                    if (script.isNotBlank()) js.add(script)
                }
            }

            val css = mutableListOf<String>()
            val cssArray = scriptObj.optJSONArray("css")
            if (cssArray != null) {
                for (j in 0 until cssArray.length()) {
                    val style = cssArray.optString(j, "").trim()
                    if (style.isNotBlank()) css.add(style)
                }
            }

            val runAt = scriptObj.optString("run_at", "document_idle").trim()
            val allFrames = scriptObj.optBoolean("all_frames", false)
            val matchAboutBlank = scriptObj.optBoolean("match_about_blank", false)
            val matchOriginAsFallback = scriptObj.optBoolean("match_origin_as_fallback", false)
            val world = scriptObj.optString("world", "ISOLATED").uppercase().trim()

            val excludeMatches = parseStringList(scriptObj, "exclude_matches")
            val includeGlobs = parseStringList(scriptObj, "include_globs")
            val excludeGlobs = parseStringList(scriptObj, "exclude_globs")

            if (matches.isNotEmpty() && (js.isNotEmpty() || css.isNotEmpty())) {
                result.add(
                    ContentScriptSpec(
                        matches = matches,
                        js = js,
                        css = css,
                        runAt = runAt,
                        allFrames = allFrames,
                        matchAboutBlank = matchAboutBlank,
                        excludeMatches = excludeMatches,
                        includeGlobs = includeGlobs,
                        excludeGlobs = excludeGlobs,
                        matchOriginAsFallback = matchOriginAsFallback,
                        world = world
                    )
                )
            }
        }
        return result
    }

    private fun parseWebAccessibleResources(root: JSONObject, manifestVersion: Int): List<WebAccessibleResourceSpec> {
        val list = mutableListOf<WebAccessibleResourceSpec>()

        if (manifestVersion == 2) {
            val arr = root.optJSONArray("web_accessible_resources") ?: return list
            val resList = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val r = arr.optString(i, "").trim()
                if (r.isNotBlank()) resList.add(r)
            }
            if (resList.isNotEmpty()) {
                list.add(WebAccessibleResourceSpec(resources = resList, matches = listOf("<all_urls>")))
            }
        } else {
            val arr = root.optJSONArray("web_accessible_resources") ?: return list
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val resources = parseStringList(obj, "resources")
                    val matches = parseStringList(obj, "matches")
                    val extensionIds = parseStringList(obj, "extension_ids")
                    val useDynamicUrl = obj.optBoolean("use_dynamic_url", false)
                    list.add(
                        WebAccessibleResourceSpec(
                            resources = resources,
                            matches = matches,
                            extensionIds = extensionIds,
                            useDynamicUrl = useDynamicUrl
                        )
                    )
                } else {
                    val singleStr = arr.optString(i, "").trim()
                    if (singleStr.isNotBlank()) {
                        list.add(WebAccessibleResourceSpec(resources = listOf(singleStr), matches = listOf("<all_urls>")))
                    }
                }
            }
        }
        return list
    }

    private fun parseContentSecurityPolicy(root: JSONObject, manifestVersion: Int): ContentSecurityPolicySpec {
        if (!root.has("content_security_policy")) {
            return ContentSecurityPolicySpec()
        }

        val rawCsp = root.opt("content_security_policy")
        if (rawCsp is JSONObject) {
            val extPages = rawCsp.optString("extension_pages", "")
            val sandbox = rawCsp.optString("sandbox", "")
            return ContentSecurityPolicySpec(extensionPages = extPages, sandbox = sandbox)
        } else if (rawCsp is String) {
            return ContentSecurityPolicySpec(extensionPages = rawCsp)
        }
        return ContentSecurityPolicySpec()
    }
}
