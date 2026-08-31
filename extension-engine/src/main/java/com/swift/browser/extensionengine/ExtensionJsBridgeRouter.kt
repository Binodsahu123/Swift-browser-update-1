package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical Router for JavaScript Extension Bridge API calls.
 * Routes raw JS calls safely to canonical adapter implementations.
 */
class ExtensionJsBridgeRouter(
    private val registry: ExtensionRegistry,
    private val permissionAdapter: ExtensionPermissionAdapter,
    private val storageManager: StorageManager,
    private val messageBus: MessageBus,
    private val eventManager: EventManager,
    private val tabsAdapter: ExtensionTabsAdapter,
    private val windowsAdapter: ExtensionWindowsAdapter,
    private val tabGroupsAdapter: ExtensionTabGroupsAdapter,
    private val sessionsAdapter: ExtensionSessionsAdapter,
    private val cookieAdapter: ExtensionCookieAdapter,
    private val bookmarksAdapter: ExtensionBookmarksAdapter,
    private val historyAdapter: ExtensionHistoryAdapter,
    private val downloadsAdapter: ExtensionDownloadsAdapter,
    private val dnrAdapter: ExtensionDnrAdapter,
    val webRequestAdapter: ExtensionWebRequestAdapter,
    private val scriptingAdapter: ExtensionScriptingAdapter,
    private val actionAdapter: ExtensionActionAdapter,
    private val contextMenusAdapter: ExtensionContextMenusAdapter,
    private val commandsAdapter: ExtensionCommandsAdapter,
    private val omniboxAdapter: ExtensionOmniboxAdapter,
    private val sidePanelAdapter: ExtensionSidePanelAdapter,
    private val managementAdapter: ExtensionManagementAdapter,
    private val topSitesAdapter: ExtensionTopSitesAdapter,
    private val idleAdapter: ExtensionIdleAdapter,
    private val ttsAdapter: ExtensionTtsAdapter,
    private val searchAdapter: ExtensionSearchAdapter,
    private val alarmsAdapter: ExtensionAlarmsAdapter,
    private val systemAdapter: ExtensionSystemAdapter,
    private val notificationsAdapter: ExtensionNotificationsAdapter,
    private val context: android.content.Context? = null,
    private val delegate: BrowserDelegate? = null
) {

    private fun verifyApiPermission(extensionId: String, requiredPermission: String) {
        val ext = registry.getExtension(extensionId)
            ?: throw SecurityException("SecurityError: Extension $extensionId not found")
        if (!registry.isExtensionEnabled(extensionId)) {
            throw SecurityException("SecurityError: Extension $extensionId is disabled")
        }
        if (!permissionAdapter.hasApiPermission(extensionId, requiredPermission)) {
            val manifestPerms = try {
                val manifest = JSONObject(ext.manifestJson)
                val perms = mutableListOf<String>()
                val pArr = manifest.optJSONArray("permissions")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) perms.add(pArr.optString(i, ""))
                }
                val oArr = manifest.optJSONArray("host_permissions")
                if (oArr != null) {
                    for (i in 0 until oArr.length()) perms.add(oArr.optString(i, ""))
                }
                perms
            } catch (e: Exception) {
                emptyList<String>()
            }
            if (!manifestPerms.any { it.equals(requiredPermission, ignoreCase = true) }) {
                throw SecurityException("SecurityError: Extension does not have '$requiredPermission' permission in manifest.")
            }
        }
    }

    suspend fun handleCall(
        sender: ExtensionSender,
        api: String,
        args: JSONArray,
        isPrivate: Boolean = false,
        privateSessionId: String? = null,
        browserDelegate: BrowserDelegate? = null
    ): Any? {
        val extId = sender.extensionId

        // Verify private mode access if in private session
        if (isPrivate && !permissionAdapter.isAllowedInPrivate(extId)) {
            throw SecurityException("SecurityError: Extension $extId is not allowed in private mode.")
        }

        return when {
            // Storage API
            api.startsWith("storage.") -> {
                verifyApiPermission(extId, "storage")
                val area = args.optString(0, "local")
                when {
                    api.endsWith(".get") -> storageManager.get(extId, area, args.opt(1), isPrivate, privateSessionId)
                    api.endsWith(".set") -> {
                        val items = args.optJSONObject(1) ?: JSONObject()
                        storageManager.set(extId, area, items, isPrivate, privateSessionId)
                        JSONObject().put("status", "success")
                    }
                    api.endsWith(".remove") -> {
                        val keysArray = args.optJSONArray(1)
                        val keysList = mutableListOf<String>()
                        if (keysArray != null) {
                            for (i in 0 until keysArray.length()) keysList.add(keysArray.getString(i))
                        } else {
                            val singleKey = args.optString(1, "")
                            if (singleKey.isNotBlank()) keysList.add(singleKey)
                        }
                        storageManager.remove(extId, area, keysList, isPrivate, privateSessionId)
                        JSONObject().put("status", "success")
                    }
                    api.endsWith(".clear") -> {
                        storageManager.clear(extId, area, isPrivate, privateSessionId)
                        JSONObject().put("status", "success")
                    }
                    api.endsWith(".getBytesInUse") -> {
                        val bytes = storageManager.getBytesInUse(extId, area, args.opt(1), isPrivate, privateSessionId)
                        bytes
                    }
                    else -> throw IllegalArgumentException("Unsupported storage operation: $api")
                }
            }

            // Tabs API
            api == "tabs.get" -> tabsAdapter.getTab(sender, args.opt(0))
            api == "tabs.query" -> tabsAdapter.queryTabs(sender, args.optJSONObject(0) ?: JSONObject())
            api == "tabs.create" -> tabsAdapter.createTab(sender, args.optJSONObject(0) ?: JSONObject())
            api == "tabs.remove" -> tabsAdapter.removeTabs(sender, listOf(args.opt(0)))
            api == "tabs.reload" -> {
                tabsAdapter.reloadTab(sender, args.opt(0))
                JSONObject().put("status", "reloaded")
            }
            api == "tabs.update" -> tabsAdapter.updateTab(sender, args.opt(0), args.optJSONObject(1) ?: JSONObject())

            // Windows API
            api == "windows.get" -> windowsAdapter.getWindow(sender, args.optInt(0, 1), args.optBoolean(1, false))
            api == "windows.getCurrent" -> windowsAdapter.getCurrentWindow(sender, args.optBoolean(0, false))
            api == "windows.getLastFocused" -> windowsAdapter.getLastFocusedWindow(sender, args.optBoolean(0, false))
            api == "windows.getAll" -> windowsAdapter.getAllWindows(sender, args.optBoolean(0, false))
            api == "windows.update" -> windowsAdapter.updateWindow(sender, args.optInt(0, 1), args.optJSONObject(1) ?: JSONObject())
            api == "windows.remove" -> {
                windowsAdapter.removeWindow(sender, args.optInt(0, 1))
                JSONObject().put("status", "removed")
            }

            // TabGroups API
            api.startsWith("tabGroups.") -> {
                when {
                    api.endsWith(".query") -> tabGroupsAdapter.queryGroups(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".get") -> tabGroupsAdapter.getGroup(sender, args.opt(0))
                    api.endsWith(".update") -> tabGroupsAdapter.updateGroup(sender, args.opt(0), args.optJSONObject(1) ?: JSONObject())
                    api.endsWith(".move") -> tabGroupsAdapter.moveGroup(sender, args.opt(0), args.optJSONObject(1) ?: JSONObject())
                    else -> throw IllegalArgumentException("Unsupported tabGroups API: $api")
                }
            }

            // Sessions API
            api.startsWith("sessions.") -> {
                when {
                    api.endsWith(".restore") -> {
                        val sessionId = if (args.length() > 0) args.optString(0, null) else null
                        sessionsAdapter.restore(sender, sessionId)
                    }
                    api.endsWith(".getDevices") -> sessionsAdapter.getDevices(sender)
                    api.endsWith(".getRecentlyClosed") -> sessionsAdapter.getRecentlyClosed(sender, args.optJSONObject(0))
                    else -> throw IllegalArgumentException("Unsupported sessions API: $api")
                }
            }

            // Scripting API
            api.startsWith("scripting.") -> {
                val activeContext = context ?: throw IllegalStateException("Context required for scripting API")
                val activeDelegate = browserDelegate ?: delegate
                when {
                    api.endsWith(".executeScript") -> {
                        var scriptResult: JSONArray? = null
                        var scriptError: String? = null
                        val latch = java.util.concurrent.CountDownLatch(1)
                        scriptingAdapter.executeScript(
                            sender = sender,
                            spec = args.optJSONObject(0) ?: JSONObject(),
                            delegate = activeDelegate,
                            context = activeContext,
                            callback = { res, err ->
                                scriptResult = res
                                scriptError = err
                                latch.countDown()
                            }
                        )
                        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                        if (scriptError != null) throw IllegalArgumentException(scriptError)
                        scriptResult ?: JSONArray()
                    }
                    api.endsWith(".insertCSS") -> scriptingAdapter.insertCSS(sender, args.optJSONObject(0) ?: JSONObject(), activeDelegate, activeContext)
                    api.endsWith(".removeCSS") -> scriptingAdapter.removeCSS(sender, args.optJSONObject(0) ?: JSONObject(), activeDelegate, activeContext)
                    api.endsWith(".registerContentScripts") -> scriptingAdapter.registerContentScripts(sender, args.optJSONArray(0) ?: JSONArray())
                    api.endsWith(".unregisterContentScripts") -> scriptingAdapter.unregisterContentScripts(sender, if (args.opt(0) is JSONArray) args.optJSONArray(0) else null)
                    api.endsWith(".updateContentScripts") -> scriptingAdapter.updateContentScripts(sender, args.optJSONArray(0) ?: JSONArray())
                    api.endsWith(".getRegisteredContentScripts") -> scriptingAdapter.getRegisteredContentScripts(sender)
                    else -> throw IllegalArgumentException("Unsupported scripting API: $api")
                }
            }

            // Cookies API
            api.startsWith("cookies.") -> {
                when {
                    api.endsWith(".get") -> cookieAdapter.get(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getAll") -> cookieAdapter.getAll(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".set") -> cookieAdapter.set(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".remove") -> cookieAdapter.remove(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getAllCookieStores") -> cookieAdapter.getAllCookieStores(sender)
                    else -> throw IllegalArgumentException("Unsupported cookies API: $api")
                }
            }

            // Bookmarks API
            api.startsWith("bookmarks.") -> {
                when {
                    api.endsWith(".get") -> bookmarksAdapter.get(sender, args.opt(0))
                    api.endsWith(".getChildren") -> bookmarksAdapter.getChildren(sender, args.optString(0, "1"))
                    api.endsWith(".getRecent") -> bookmarksAdapter.getRecent(sender, args.optInt(0, 10))
                    api.endsWith(".getTree") -> bookmarksAdapter.getTree(sender)
                    api.endsWith(".getSubTree") -> bookmarksAdapter.getSubTree(sender, args.optString(0, "1"))
                    api.endsWith(".search") -> bookmarksAdapter.search(sender, args.opt(0))
                    api.endsWith(".create") -> bookmarksAdapter.create(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".update") -> bookmarksAdapter.update(sender, args.optString(0, ""), args.optJSONObject(1) ?: JSONObject())
                    api.endsWith(".move") -> bookmarksAdapter.move(sender, args.optString(0, ""), args.optJSONObject(1) ?: JSONObject())
                    api.endsWith(".remove") -> bookmarksAdapter.remove(sender, args.optString(0, ""))
                    api.endsWith(".removeTree") -> bookmarksAdapter.removeTree(sender, args.optString(0, ""))
                    else -> throw IllegalArgumentException("Unsupported bookmarks API: $api")
                }
            }

            // History API
            api.startsWith("history.") -> {
                when {
                    api.endsWith(".search") -> historyAdapter.search(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getVisits") -> historyAdapter.getVisits(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".addUrl") -> historyAdapter.addUrl(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".deleteUrl") -> historyAdapter.deleteUrl(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".deleteRange") -> historyAdapter.deleteRange(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".deleteAll") -> historyAdapter.deleteAll(sender)
                    else -> throw IllegalArgumentException("Unsupported history API: $api")
                }
            }

            // Downloads API
            api.startsWith("downloads.") -> {
                when {
                    api.endsWith(".download") -> downloadsAdapter.download(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".search") -> downloadsAdapter.search(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".pause") -> downloadsAdapter.pause(sender, args.optLong(0, -1L))
                    api.endsWith(".resume") -> downloadsAdapter.resume(sender, args.optLong(0, -1L))
                    api.endsWith(".cancel") -> downloadsAdapter.cancel(sender, args.optLong(0, -1L))
                    api.endsWith(".removeFile") -> downloadsAdapter.removeFile(sender, args.optLong(0, -1L))
                    api.endsWith(".erase") -> downloadsAdapter.erase(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".open") -> downloadsAdapter.open(sender, args.optLong(0, -1L))
                    else -> throw IllegalArgumentException("Unsupported downloads API: $api")
                }
            }

            // DeclarativeNetRequest API
            api.startsWith("declarativeNetRequest.") -> {
                when {
                    api.endsWith(".updateDynamicRules") -> dnrAdapter.updateDynamicRules(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getDynamicRules") -> dnrAdapter.getDynamicRules(sender)
                    api.endsWith(".updateSessionRules") -> dnrAdapter.updateSessionRules(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getSessionRules") -> dnrAdapter.getSessionRules(sender)
                    api.endsWith(".getMatchedRules") -> dnrAdapter.getMatchedRules(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".isRegexSupported") -> dnrAdapter.isRegexSupported(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".setExtensionActionOptions") -> dnrAdapter.setExtensionActionOptions(sender, args.optJSONObject(0) ?: JSONObject())
                    else -> throw IllegalArgumentException("Unsupported declarativeNetRequest API: $api")
                }
            }

            // Action / BrowserAction / PageAction API
            api.startsWith("action.") || api.startsWith("browserAction.") || api.startsWith("pageAction.") -> {
                when {
                    api.endsWith(".setIcon") -> actionAdapter.setIcon(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".setTitle") -> actionAdapter.setTitle(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getTitle") -> actionAdapter.getTitle(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".setPopup") -> actionAdapter.setPopup(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getPopup") -> actionAdapter.getPopup(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".setBadgeText") -> actionAdapter.setBadgeText(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getBadgeText") -> actionAdapter.getBadgeText(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".setBadgeBackgroundColor") -> actionAdapter.setBadgeBackgroundColor(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".enable") -> actionAdapter.enable(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".disable") -> actionAdapter.disable(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".openPopup") -> actionAdapter.openPopup(sender)
                    else -> throw IllegalArgumentException("Unsupported action API: $api")
                }
            }

            // ContextMenus API
            api.startsWith("contextMenus.") -> {
                when {
                    api.endsWith(".create") -> contextMenusAdapter.create(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".update") -> contextMenusAdapter.update(sender, args.optString(0, ""), args.optJSONObject(1) ?: JSONObject())
                    api.endsWith(".remove") -> contextMenusAdapter.remove(sender, args.optString(0, ""))
                    api.endsWith(".removeAll") -> contextMenusAdapter.removeAll(sender)
                    else -> throw IllegalArgumentException("Unsupported contextMenus API: $api")
                }
            }

            // Commands API
            api == "commands.getAll" -> commandsAdapter.getAll(sender)

            // Omnibox API
            api == "omnibox.setDefaultSuggestion" -> omniboxAdapter.setDefaultSuggestion(sender, args.optJSONObject(0) ?: JSONObject())

            // SidePanel API
            api.startsWith("sidePanel.") -> {
                when {
                    api.endsWith(".setOptions") -> sidePanelAdapter.setOptions(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getOptions") -> sidePanelAdapter.getOptions(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".setPanelBehavior") -> sidePanelAdapter.setPanelBehavior(sender, args.optJSONObject(0) ?: JSONObject())
                    api.endsWith(".getPanelBehavior") -> sidePanelAdapter.getPanelBehavior(sender)
                    api.endsWith(".open") -> sidePanelAdapter.open(sender, args.optJSONObject(0) ?: JSONObject())
                    else -> throw IllegalArgumentException("Unsupported sidePanel API: $api")
                }
            }

            // Management API
            api.startsWith("management.") -> {
                when {
                    api.endsWith(".get") -> managementAdapter.get(sender, args.optString(0, ""))
                    api.endsWith(".getSelf") -> managementAdapter.getSelf(sender)
                    api.endsWith(".getAll") -> managementAdapter.getAll(sender)
                    api.endsWith(".setEnabled") -> managementAdapter.setEnabled(sender, args.optString(0, ""), args.optBoolean(1, true))
                    api.endsWith(".uninstall") -> managementAdapter.uninstall(sender, args.optString(0, ""), args.optJSONObject(1))
                    else -> throw IllegalArgumentException("Unsupported management API: $api")
                }
            }

            // TopSites API
            api == "topSites.get" -> topSitesAdapter.get(sender, browserDelegate ?: delegate)

            // Idle API
            api.startsWith("idle.") -> {
                when {
                    api.endsWith(".queryState") -> idleAdapter.queryState(sender, args.optInt(0, 60), context)
                    api.endsWith(".setDetectionInterval") -> idleAdapter.setDetectionInterval(sender, args.optInt(0, 60))
                    else -> throw IllegalArgumentException("Unsupported idle API: $api")
                }
            }

            // TTS API
            api.startsWith("tts.") -> {
                when {
                    api.endsWith(".speak") -> ttsAdapter.speak(sender, args.optString(0, ""), args.optJSONObject(1) ?: JSONObject(), context)
                    api.endsWith(".stop") -> ttsAdapter.stop(sender)
                    api.endsWith(".pause") -> ttsAdapter.pause(sender)
                    api.endsWith(".resume") -> ttsAdapter.resume(sender)
                    api.endsWith(".isSpeaking") -> ttsAdapter.isSpeaking(sender)
                    api.endsWith(".getVoices") -> ttsAdapter.getVoices(sender)
                    else -> throw IllegalArgumentException("Unsupported tts API: $api")
                }
            }

            // Search API
            api == "search.query" -> searchAdapter.query(sender, args.optJSONObject(0) ?: JSONObject(), browserDelegate ?: delegate)

            // Alarms API
            api.startsWith("alarms.") -> {
                when {
                    api.endsWith(".create") -> alarmsAdapter.create(sender, args.optString(0, ""), args.optJSONObject(1) ?: JSONObject())
                    api.endsWith(".get") -> alarmsAdapter.get(sender, args.optString(0, ""))
                    api.endsWith(".getAll") -> alarmsAdapter.getAll(sender)
                    api.endsWith(".clear") -> alarmsAdapter.clear(sender, args.optString(0, ""))
                    api.endsWith(".clearAll") -> alarmsAdapter.clearAll(sender)
                    else -> throw IllegalArgumentException("Unsupported alarms API: $api")
                }
            }

            // System API
            api.startsWith("system.") -> {
                when {
                    api.endsWith(".cpu.getInfo") -> systemAdapter.getCpuInfo(sender, context)
                    api.endsWith(".memory.getInfo") -> systemAdapter.getMemoryInfo(sender, context)
                    api.endsWith(".storage.getInfo") -> systemAdapter.getStorageInfo(sender, context)
                    else -> throw IllegalArgumentException("Unsupported system API: $api")
                }
            }

            // Notifications API
            api.startsWith("notifications.") -> {
                val activeDelegate = browserDelegate ?: delegate
                when {
                    api.endsWith(".create") -> notificationsAdapter.create(sender, if (args.opt(0) is String) args.optString(0) else null, args.optJSONObject(if (args.opt(0) is String) 1 else 0) ?: JSONObject(), activeDelegate)
                    api.endsWith(".update") -> notificationsAdapter.update(sender, args.optString(0, ""), args.optJSONObject(1) ?: JSONObject())
                    api.endsWith(".clear") -> notificationsAdapter.clear(sender, args.optString(0, ""))
                    api.endsWith(".getAll") -> notificationsAdapter.getAll(sender)
                    else -> throw IllegalArgumentException("Unsupported notifications API: $api")
                }
            }

            // Extension / Permissions Queries
            api == "extension.isAllowedIncognitoAccess" -> permissionAdapter.isAllowedInPrivate(extId)
            api == "extension.isAllowedFileSchemeAccess" -> {
                val ext = registry.getExtension(extId)
                if (ext != null) {
                    permissionAdapter.hasHostPermission(extId, "file://*")
                } else false
            }

            else -> throw IllegalArgumentException("UNSUPPORTED_BY_ORION")
        }
    }
}
