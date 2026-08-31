package com.swift.browser.extensionengine

/**
 * Canonical Chrome Extension Version Parser and Comparator.
 *
 * Rules:
 * - 1 to 4 dot-separated integers (0..65535).
 * - No leading zeros for numbers > 0 (e.g., "01" is invalid, "0" is valid).
 * - Canonical comparison: missing components are treated as 0 (e.g., "1.0" == "1.0.0").
 */
object ExtensionVersionComparator {

    /**
     * Validates if a version string strictly complies with Chrome extension version rules.
     */
    fun isValidVersion(versionStr: String): Boolean {
        return parseVersion(versionStr) != null
    }

    /**
     * Parses a version string into a normalized 4-element IntArray.
     * Returns null if the version string is malformed.
     */
    fun parseVersion(versionStr: String): IntArray? {
        val trimmed = versionStr.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(".")
        if (parts.isEmpty() || parts.size > 4) return null

        val result = IntArray(4) { 0 }
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isEmpty()) return null

            // Disallow leading zeros for numbers > 0 (e.g. "01")
            if (part.length > 1 && part.startsWith("0")) return null

            // Validate all characters are digits
            if (!part.all { it.isDigit() }) return null

            val num = part.toLongOrNull() ?: return null
            if (num < 0 || num > 65535) return null

            result[i] = num.toInt()
        }

        return result
    }

    /**
     * Compares two extension versions according to canonical Chrome extension rules.
     * Returns:
     * - positive integer if v1 > v2
     * - zero if v1 == v2 (e.g. "1.0" and "1.0.0")
     * - negative integer if v1 < v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parsed1 = parseVersion(v1) ?: parseFallback(v1)
        val parsed2 = parseVersion(v2) ?: parseFallback(v2)

        for (i in 0 until 4) {
            val diff = parsed1[i].compareTo(parsed2[i])
            if (diff != 0) return diff
        }
        return 0
    }

    /**
     * Checks if the candidate version is strictly newer than the current version.
     */
    fun isNewerVersion(candidateVersion: String, currentVersion: String): Boolean {
        return compareVersions(candidateVersion, currentVersion) > 0
    }

    private fun parseFallback(versionStr: String): IntArray {
        val result = IntArray(4) { 0 }
        val parts = versionStr.split(".")
        for (i in 0 until minOf(4, parts.size)) {
            result[i] = parts[i].filter { it.isDigit() }.toIntOrNull() ?: 0
        }
        return result
    }
}
