package com.swift.browser.adblockengine.filters

/**
 * Validates rule blocks to prevent corrupted downloads from breaking match indices.
 */
object FilterListValidator {
    fun isValidRule(rule: String): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("!")) return false // comment
        
        // Ensure no weird binary headers or extremely long malformed strings
        if (trimmed.length > 1000) return false
        
        return true
    }
}
