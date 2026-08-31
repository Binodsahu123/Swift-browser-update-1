package com.swift.browser.adblockengine.cosmetic

/**
 * Parses and processes cosmetic hiding expressions.
 */
object CosmeticFilterParser {
    fun parseSelector(rule: String): String? {
        val parts = rule.split("##")
        if (parts.size >= 2) {
            return parts[1].trim()
        }
        return null
    }
}
