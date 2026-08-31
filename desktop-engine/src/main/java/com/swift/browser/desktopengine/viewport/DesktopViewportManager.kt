package com.swift.browser.desktopengine.viewport

object DesktopViewportManager {
    fun getViewportScript(isDesktop: Boolean): String {
        return if (isDesktop) {
            ViewportManager.getDesktopViewportScript()
        } else {
            ViewportManager.getMobileViewportRestoreScript()
        }
    }
}
