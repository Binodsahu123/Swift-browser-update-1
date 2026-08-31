package com.swift.browser.desktopengine.useragent

import android.content.Context
import com.swift.browser.desktopengine.api.DesktopMode

object UserAgentPolicy {
    fun resolveUserAgent(host: String, mode: DesktopMode, context: Context? = null): String {
        return when (mode) {
            DesktopMode.DESKTOP -> UserAgentManager.getDesktopUserAgent(host, context)
            DesktopMode.MOBILE -> UserAgentManager.getMobileUserAgent(context)
        }
    }
}
