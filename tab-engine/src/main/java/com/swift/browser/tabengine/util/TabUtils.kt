package com.swift.browser.tabengine.util

object TabUtils {
    fun generateTabId(): String = java.util.UUID.randomUUID().toString()
}
