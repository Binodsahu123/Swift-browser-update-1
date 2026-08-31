package com.swift.browser.extensionengine

import androidx.webkit.WebViewFeature

/**
 * Capability Matrix query engine for AndroidX WebView script execution capabilities.
 * Guarantees feature safety before invoking platform WebView APIs for frames, worlds, and timing phases.
 */
object OrionWebViewScriptingCapabilities {

    fun isDocumentStartScriptSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        } catch (e: Throwable) {
            false
        }
    }

    fun isFrameAndWorldInjectionSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)
        } catch (e: Throwable) {
            false
        }
    }

    fun isWebMessageListenerSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        } catch (e: Throwable) {
            false
        }
    }

    fun canTargetFrame(frameId: Int, allFrames: Boolean): Boolean {
        if (!allFrames && frameId == 0) return true
        return isFrameAndWorldInjectionSupported()
    }

    fun canTargetWorld(world: String): Boolean {
        val w = world.uppercase().trim()
        if (w == "MAIN") return true
        if (w == "ISOLATED") return isFrameAndWorldInjectionSupported()
        return false
    }
}
