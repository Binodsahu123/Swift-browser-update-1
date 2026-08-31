package com.swift.browser.extensionengine

/**
 * Android WebView public APIs do not expose true V8 isolated JavaScript contexts.
 * We explicitly expose ISOLATED_WORLD_UNAVAILABLE when querying isolated world semantics.
 */
const val ISOLATED_WORLD_UNAVAILABLE = "ISOLATED_WORLD_UNAVAILABLE"

enum class ContentScriptWorld {
    MAIN,
    ISOLATED;

    companion object {
        fun fromString(worldStr: String): ContentScriptWorld {
            return when (worldStr.uppercase().trim()) {
                "MAIN" -> MAIN
                else -> ISOLATED
            }
        }
    }
}
