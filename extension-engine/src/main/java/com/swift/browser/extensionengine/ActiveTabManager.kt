package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject

class ActiveTabManager(private val delegate: BrowserDelegate?) {

    fun getActiveTabId(): String? {
        return delegate?.getActiveTabId()
    }

    fun queryActiveTabs(): JSONArray {
        val queryInfo = JSONObject().apply {
            put("active", true)
        }
        return delegate?.queryTabs(queryInfo) ?: JSONArray()
    }

    fun executeScriptOnActiveTab(code: String, callback: (String?) -> Unit) {
        val activeId = getActiveTabId()
        if (activeId != null) {
            delegate?.executeScriptOnTab(activeId, code, callback)
        } else {
            callback(null)
        }
    }
}
