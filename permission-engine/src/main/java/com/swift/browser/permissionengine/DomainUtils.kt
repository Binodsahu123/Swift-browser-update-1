package com.swift.browser.permissionengine

object DomainUtils {
    fun getDomain(urlStr: String): String {
        return OriginNormalizer.normalize(urlStr)
    }

    fun getCanonicalHost(host: String): String {
        val h = host.lowercase().trim()
        return when {
            h.startsWith("m.") -> h.substring(2)
            h.startsWith("mobile.") -> h.substring(7)
            h.startsWith("www.") -> h.substring(4)
            else -> h
        }
    }
}

