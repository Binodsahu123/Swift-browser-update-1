package com.swift.browser.extensionengine

data class ContentScriptDefinition(
    val extensionId: String,
    val scriptId: String,
    val jsFiles: List<String>,
    val cssFiles: List<String>,
    val matches: List<String>,
    val excludeMatches: List<String> = emptyList(),
    val includeGlobs: List<String> = emptyList(),
    val excludeGlobs: List<String> = emptyList(),
    val runAt: String = "document_idle",
    val allFrames: Boolean = false,
    val matchAboutBlank: Boolean = false,
    val matchOriginAsFallback: Boolean = false,
    val world: String = "ISOLATED",
    val persist: Boolean = true,
    val enabled: Boolean = true
) {
    fun matchesUrl(urlStr: String, targetOrigin: String? = null): Boolean {
        if (urlStr.isBlank()) return false

        var urlToTest = urlStr
        if (urlStr == "about:blank" || urlStr.startsWith("about:")) {
            if (!matchAboutBlank && !matchOriginAsFallback) return false
            if (targetOrigin != null && targetOrigin.isNotBlank()) {
                urlToTest = targetOrigin
            } else {
                return false
            }
        }

        // 1. Matches pattern check
        val matchesPattern = ExtensionMatchPattern.matchesAny(urlToTest, matches)
        if (!matchesPattern) return false

        // 2. Exclude matches check
        if (excludeMatches.isNotEmpty() && ExtensionMatchPattern.matchesAny(urlToTest, excludeMatches)) {
            return false
        }

        // 3. Include globs check
        if (includeGlobs.isNotEmpty()) {
            val matchesGlob = includeGlobs.any { globToRegex(it).containsMatchIn(urlToTest) }
            if (!matchesGlob) return false
        }

        // 4. Exclude globs check
        if (excludeGlobs.isNotEmpty()) {
            val matchesExcludeGlob = excludeGlobs.any { globToRegex(it).containsMatchIn(urlToTest) }
            if (matchesExcludeGlob) return false
        }

        return true
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        for (char in glob) {
            when (char) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.' -> sb.append("\\.")
                '\\' -> sb.append("\\\\")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '[' -> sb.append("\\[")
                ']' -> sb.append("\\]")
                '{' -> sb.append("\\{")
                '}' -> sb.append("\\}")
                '+' -> sb.append("\\+")
                '^' -> sb.append("\\^")
                '$' -> sb.append("\\$")
                '|' -> sb.append("\\|")
                else -> sb.append(char)
            }
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
