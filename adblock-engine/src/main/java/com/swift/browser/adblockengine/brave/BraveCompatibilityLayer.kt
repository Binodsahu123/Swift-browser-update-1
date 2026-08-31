package com.swift.browser.adblockengine.brave

/**
 * Isolates and mitigates library version changes and cross-compatibility issues.
 */
object BraveCompatibilityLayer {
    fun runSafe(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
