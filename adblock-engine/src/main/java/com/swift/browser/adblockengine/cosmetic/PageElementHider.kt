package com.swift.browser.adblockengine.cosmetic

/**
 * Synthesizes injection Javascript sequences to dynamically manipulate the DOM.
 */
object PageElementHider {
    fun createHidingScript(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""
        
        val builder = StringBuilder()
        builder.append("(function() {")
        builder.append("  const selectors = [")
        for (i in selectors.indices) {
            builder.append("'").append(selectors[i].replace("'", "\\'")).append("'")
            if (i < selectors.size - 1) builder.append(",")
        }
        builder.append("];\n")
        builder.append("  selectors.forEach(sel => {\n")
        builder.append("    try {\n")
        builder.append("      document.querySelectorAll(sel).forEach(el => {\n")
        builder.append("        el.style.setProperty('display', 'none', 'important');\n")
        builder.append("      });\n")
        builder.append("    } catch(e) {}\n")
        builder.append("  });\n")
        builder.append("})();")
        return builder.toString()
    }
}
