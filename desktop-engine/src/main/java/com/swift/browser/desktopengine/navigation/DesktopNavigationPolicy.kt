package com.swift.browser.desktopengine.navigation

import android.net.Uri

object DesktopNavigationPolicy {

    fun resolveDesktopUrl(urlStr: String, isDesktop: Boolean): String {
        return urlStr
    }

    fun isInternalSubpathNavigation(currentUrl: String, targetUrl: String): Boolean {
        if (currentUrl.isEmpty() || targetUrl.isEmpty()) return false
        return try {
            val uri1 = Uri.parse(currentUrl)
            val uri2 = Uri.parse(targetUrl)
            val host1 = uri1.host?.lowercase().orEmpty()
            val host2 = uri2.host?.lowercase().orEmpty()
            
            // If hosts match or are subdomains of same main domain, it's internal navigation
            host1 == host2 || isSameBaseDomain(host1, host2)
        } catch (_: Exception) {
            false
        }
    }

    private fun isSameBaseDomain(h1: String, h2: String): Boolean {
        val b1 = getBaseDomain(h1)
        val b2 = getBaseDomain(h2)
        return b1.isNotEmpty() && b1 == b2
    }

    private fun getBaseDomain(host: String): String {
        val parts = host.split(".")
        if (parts.size >= 2) {
            return parts.takeLast(2).joinToString(".")
        }
        return host
    }
}


