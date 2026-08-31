package com.swift.browser.browserengine

import android.util.Log
import android.webkit.WebView
import com.swift.browser.securityengine.SwiftSecurityEngine

enum class NavigationSource {
    USER_INPUT,
    SEARCH_SUBMIT,
    BOOKMARK,
    HISTORY,
    NEW_TAB,
    CONTEXT_MENU,
    EXTENSION,
    WEBVIEW_INTERNAL,
    WEBVIEW_REDIRECT,
    DESKTOP_MODE_CHANGE,
    RECOVERY,
    INTENT,
    SYSTEM
}

data class NavigationRequest(
    val tabId: String = "",
    val url: String = "",
    val source: NavigationSource = NavigationSource.USER_INPUT,
    val applyDesktopPolicy: Boolean = false,
    val forceReload: Boolean = false,
    val webView: WebView? = null
)

object BrowserNavigationApi {
    private const val TAG = "BrowserNavigationApi"

    fun navigate(request: NavigationRequest) {
        Log.d(TAG, "NAV_REQUEST source=${request.source} tabId=${request.tabId} url=${request.url}")
        BrowserNavigationEngine.navigate(request)
    }
}

class BrowserNavigationEngine(private val stateEngine: BrowserStateEngine) {
    companion object {
        private const val TAG = "BrowserNavigationEngine"

        fun normalizeUrl(url: String): String {
            if (url.isBlank()) return ""
            val trimmed = url.trim()
            return try {
                val uri = java.net.URI(trimmed)
                val scheme = uri.scheme?.lowercase() ?: "https"
                val host = uri.host?.lowercase().orEmpty()
                val path = uri.path?.trimEnd('/').orEmpty()
                val query = uri.query.orEmpty()
                val normalizedScheme = if (scheme == "http") "https" else scheme
                val hostPart = if (host.isNotEmpty()) "://$host" else ""
                "$normalizedScheme$hostPart$path${if (query.isNotEmpty()) "?$query" else ""}"
            } catch (_: Exception) {
                try {
                    val uri = android.net.Uri.parse(trimmed)
                    val scheme = uri.scheme?.lowercase() ?: "https"
                    val host = uri.host?.lowercase().orEmpty()
                    val path = uri.path?.trimEnd('/').orEmpty()
                    val query = uri.query.orEmpty()
                    val normalizedScheme = if (scheme == "http") "https" else scheme
                    "$normalizedScheme://$host$path${if (query.isNotEmpty()) "?$query" else ""}"
                } catch (_: Exception) {
                    trimmed.trimEnd('/')
                }
            }
        }

        fun navigate(request: NavigationRequest) {
            val webView = request.webView ?: return
            val trimmed = cleanUrlStatic(request.url)
            if (trimmed.isEmpty()) return

            val currentUrl = webView.url.orEmpty()
            var finalUrl = trimmed

            // Only top-level intentional user navigation sources apply desktop/mobile URL host policy
            val allowsDesktopPolicy = request.applyDesktopPolicy && (
                request.source == NavigationSource.USER_INPUT ||
                request.source == NavigationSource.SEARCH_SUBMIT ||
                request.source == NavigationSource.BOOKMARK ||
                request.source == NavigationSource.HISTORY ||
                request.source == NavigationSource.NEW_TAB ||
                request.source == NavigationSource.CONTEXT_MENU ||
                request.source == NavigationSource.DESKTOP_MODE_CHANGE
            )

            if (allowsDesktopPolicy) {
                if (!finalUrl.startsWith("swift://") && !finalUrl.startsWith("about:")) {
                    val decision = com.swift.browser.desktopengine.api.DesktopEngineProvider.api.resolveNavigationTarget(
                        tabId = request.tabId,
                        currentUrl = currentUrl,
                        requestedUrl = finalUrl,
                        source = request.source.name
                    )
                    if (!decision.shouldLoad) {
                        return
                    }
                    finalUrl = decision.finalUrl
                }
            }

            // Compare AFTER resolution to prevent redundant reloads
            if (!request.forceReload && currentUrl.isNotEmpty()) {
                val normCurrent = normalizeUrl(currentUrl)
                val normTarget = normalizeUrl(finalUrl)
                if (normCurrent == normTarget) {
                    Log.d(TAG, "NAV_SKIP_SAME tabId=${request.tabId} target=$finalUrl")
                    return
                }
            }

            if (!SwiftSecurityEngine.isUrlSafe(finalUrl) && !finalUrl.startsWith("swift://") && !finalUrl.startsWith("about:")) {
                webView.post {
                    ErrorPageEngine.loadErrorPage(webView, "security", finalUrl)
                }
                return
            }

            Log.d(TAG, "NAV_LOAD tabId=${request.tabId} source=${request.source} finalUrl=$finalUrl")
            webView.post {
                try {
                    webView.loadUrl(finalUrl)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun safeLoadUrl(webView: WebView?, url: String, isDesktopMode: Boolean = false, forceReload: Boolean = false) {
            if (webView == null) return
            navigate(
                NavigationRequest(
                    url = url,
                    source = NavigationSource.RECOVERY,
                    applyDesktopPolicy = false,
                    forceReload = forceReload,
                    webView = webView
                )
            )
        }

        fun cleanUrlStatic(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return "about:blank"
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://") || trimmed.startsWith("content://") || trimmed.startsWith("about:")) {
                return trimmed
            }
            if (trimmed.contains(".") && !trimmed.contains(" ")) {
                return "https://$trimmed"
            }
            return "https://www.google.com/search?q=" + android.net.Uri.encode(trimmed)
        }
    }

    fun cleanUrl(input: String): String {
        return cleanUrlStatic(input)
    }

    fun loadUrl(tabId: String, rawUrl: String, adapter: Any? = null) {
        val finalUrl = cleanUrl(rawUrl)
        Log.d(TAG, "Navigating tab $tabId to $finalUrl")
        stateEngine.updateUrl(finalUrl)
        stateEngine.updateLoading(true, 10)
        stateEngine.setError(null)
        stateEngine.updateDiagnostics("Navigating to $finalUrl")
    }

    fun reloadPage(tabId: String) {
        Log.d(TAG, "Reloading tab $tabId")
        stateEngine.updateLoading(true, 10)
        stateEngine.updateDiagnostics("Reloading page")
    }

    fun stopLoading(tabId: String) {
        Log.d(TAG, "Stopping load on tab $tabId")
        stateEngine.updateLoading(false)
        stateEngine.updateState(BrowserState.STOPPED)
        stateEngine.updateDiagnostics("Loading stopped by user")
    }
}

