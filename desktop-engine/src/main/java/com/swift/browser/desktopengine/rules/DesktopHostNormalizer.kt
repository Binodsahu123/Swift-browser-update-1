package com.swift.browser.desktopengine.rules

object DesktopHostNormalizer {
    fun getCanonicalHost(host: String): String {
        val h = host.lowercase().trim()
        return when {
            h == "facebook.com" || h == "www.facebook.com" || h == "m.facebook.com" -> "facebook.com"
            h == "reddit.com" || h == "www.reddit.com" || h == "m.reddit.com" -> "reddit.com"
            h == "twitter.com" || h == "www.twitter.com" || h == "m.twitter.com" || h == "mobile.twitter.com" ||
            h == "x.com" || h == "www.x.com" || h == "m.x.com" || h == "mobile.x.com" -> "x.com"
            h.endsWith(".wikipedia.org") || h == "wikipedia.org" -> "wikipedia.org"
            h.startsWith("m.") -> h.substring(2)
            h.startsWith("mobile.") -> h.substring(7)
            h.startsWith("www.") -> h.substring(4)
            else -> h
        }
    }
}
