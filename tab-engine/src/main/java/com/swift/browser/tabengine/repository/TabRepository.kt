package com.swift.browser.tabengine.repository

import android.content.Context
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.tabengine.model.TabGroupModel
import org.json.JSONArray
import org.json.JSONObject

class TabRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("tab_engine_session", Context.MODE_PRIVATE)

    fun saveGroups(groups: List<TabGroupModel>) {
        try {
            val array = JSONArray()
            groups.filter { !it.isPrivate && !it.isIncognito && it.privateSessionId == null }.forEach { group ->
                val normalTabs = group.tabs.filter { !it.isPrivate && !it.isIncognito && it.privateSessionId == null }
                if (normalTabs.isEmpty()) return@forEach

                val groupObj = JSONObject()
                groupObj.put("id", group.id)
                groupObj.put("name", group.name)
                groupObj.put("color", group.color)
                groupObj.put("isIncognito", false)
                groupObj.put("isPrivate", false)
                groupObj.put("activeTabId", if (normalTabs.any { it.id == group.activeTabId }) group.activeTabId ?: "" else normalTabs.firstOrNull()?.id ?: "")
                groupObj.put("isCollapsed", group.isCollapsed)
                
                val tabsArray = JSONArray()
                normalTabs.forEach { tab ->
                    val tabObj = JSONObject()
                    tabObj.put("id", tab.id)
                    tabObj.put("url", tab.url)
                    tabObj.put("title", tab.title)
                    tabObj.put("isIncognito", false)
                    tabObj.put("isPrivate", false)
                    tabsArray.put(tabObj)
                }
                groupObj.put("tabs", tabsArray)
                array.put(groupObj)
            }
            prefs.edit().putString("groups", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadGroups(): List<TabGroupModel> {
        val groups = mutableListOf<TabGroupModel>()
        try {
            val jsonStr = prefs.getString("groups", null) ?: return emptyList()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val groupObj = array.optJSONObject(i) ?: continue
                val groupIsIncognito = groupObj.optBoolean("isIncognito", false)
                val groupIsPrivate = groupObj.optBoolean("isPrivate", false)
                val groupSessionId = groupObj.optString("privateSessionId", "")
                if (groupIsIncognito || groupIsPrivate || groupSessionId.isNotEmpty()) {
                    // Ignore legacy or private groups
                    continue
                }

                val tabsArray = groupObj.optJSONArray("tabs")
                val tabs = mutableListOf<TabModel>()
                if (tabsArray != null) {
                    for (j in 0 until tabsArray.length()) {
                        val tabObj = tabsArray.optJSONObject(j) ?: continue
                        val tabIsIncognito = tabObj.optBoolean("isIncognito", false)
                        val tabIsPrivate = tabObj.optBoolean("isPrivate", false)
                        val tabSessionId = tabObj.optString("privateSessionId", "")
                        if (tabIsIncognito || tabIsPrivate || tabSessionId.isNotEmpty()) {
                            // Ignore legacy or private tabs
                            continue
                        }

                        val tabId = tabObj.optString("id", java.util.UUID.randomUUID().toString())
                        val tabUrl = tabObj.optString("url", "swift://newtab")
                        val tabTitle = tabObj.optString("title", "New Tab")
                        tabs.add(TabModel(
                            id = tabId,
                            url = tabUrl,
                            title = tabTitle,
                            isIncognito = false,
                            isPrivate = false,
                            privateSessionId = null
                        ))
                    }
                }
                
                if (tabs.isEmpty()) {
                    continue
                }

                val activeTabIdStr = groupObj.optString("activeTabId", "")
                val groupId = groupObj.optString("id", java.util.UUID.randomUUID().toString())
                
                val validActiveTabId = if (tabs.any { it.id == activeTabIdStr }) activeTabIdStr else tabs.first().id

                groups.add(TabGroupModel(
                    id = groupId,
                    name = groupObj.optString("name", "Group"),
                    color = groupObj.optLong("color", 0xFFCCCCCC),
                    isIncognito = false,
                    isPrivate = false,
                    privateSessionId = null,
                    tabs = tabs,
                    activeTabId = validActiveTabId,
                    isCollapsed = groupObj.optBoolean("isCollapsed", false)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return groups
    }
}
