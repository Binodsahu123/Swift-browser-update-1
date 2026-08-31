package com.swift.browser.desktopengine.navigation

import android.webkit.WebView
import com.swift.browser.desktopengine.api.DesktopMode
import com.swift.browser.desktopengine.api.DesktopTransitionReason
import com.swift.browser.desktopengine.diagnostics.DesktopDiagnostics
import com.swift.browser.desktopengine.diagnostics.DesktopModeEvent
import com.swift.browser.desktopengine.rules.DesktopSiteRule
import com.swift.browser.desktopengine.state.DesktopModeTransitionState
import com.swift.browser.desktopengine.state.DesktopNavigationState
import com.swift.browser.desktopengine.useragent.UserAgentMetadataPolicy
import com.swift.browser.desktopengine.useragent.UserAgentPolicy
import com.swift.browser.desktopengine.viewport.DesktopCssEnvironment
import com.swift.browser.desktopengine.viewport.DesktopViewportPolicy
import com.swift.browser.desktopengine.viewport.DeviceMetricsManager
import com.swift.browser.desktopengine.webview.DesktopWebViewConfigurator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object DesktopModeTransition {
    private val tabTransitionStates = ConcurrentHashMap<String, MutableStateFlow<DesktopModeTransitionState>>()

    private val tabGenerations = ConcurrentHashMap<String, AtomicLong>()
    private val tabAppliedModeMap = ConcurrentHashMap<String, DesktopMode>()
    private val tabLastTargetUrlMap = ConcurrentHashMap<String, String>()
    private val pendingRestoreGenerations = ConcurrentHashMap<String, Long>()

    fun getTabTransitionState(tabId: String): StateFlow<DesktopModeTransitionState> {
        val effectiveTabId = if (tabId.isEmpty()) "default" else tabId
        return tabTransitionStates.computeIfAbsent(effectiveTabId) {
            MutableStateFlow(DesktopModeTransitionState.Idle)
        }
    }

    private fun setTabState(tabId: String, state: DesktopModeTransitionState) {
        val effectiveTabId = if (tabId.isEmpty()) "default" else tabId
        tabTransitionStates.computeIfAbsent(effectiveTabId) {
            MutableStateFlow(DesktopModeTransitionState.Idle)
        }.value = state
    }

    fun getGeneration(tabId: String): Long {
        return tabGenerations[tabId]?.get() ?: 0L
    }

    fun incrementGeneration(tabId: String): Long {
        val counter = tabGenerations.computeIfAbsent(tabId) { AtomicLong(0L) }
        return counter.incrementAndGet()
    }

    private val pendingCallbacks = ConcurrentHashMap<String, Pair<Long, () -> Unit>>()

    fun isTransitionActive(tabId: String = ""): Boolean {
        if (tabId.isNotEmpty()) {
            val state = tabTransitionStates[tabId]?.value ?: DesktopModeTransitionState.Idle
            return state != DesktopModeTransitionState.Idle &&
                   state != DesktopModeTransitionState.Completed &&
                   state !is DesktopModeTransitionState.Failed
        }
        return tabTransitionStates.values.any { stateFlow ->
            val s = stateFlow.value
            s != DesktopModeTransitionState.Idle &&
            s != DesktopModeTransitionState.Completed &&
            s !is DesktopModeTransitionState.Failed
        }
    }

    fun isGenerationCurrent(tabId: String, generation: Long): Boolean {
        if (tabId.isEmpty()) return true
        val current = tabGenerations[tabId]?.get() ?: 0L
        return current == generation
    }

    fun clearTab(tabId: String) {
        if (tabId.isEmpty()) return
        tabTransitionStates.remove(tabId)
        tabGenerations.remove(tabId)
        tabAppliedModeMap.remove(tabId)
        tabLastTargetUrlMap.remove(tabId)
        pendingRestoreGenerations.remove(tabId)
        pendingCallbacks.remove(tabId)
        DesktopNavigationState.clearState(tabId)
    }

    fun executeTransition(
        tabId: String,
        webView: WebView,
        host: String,
        targetMode: DesktopMode,
        reason: DesktopTransitionReason = DesktopTransitionReason.USER_TOGGLE,
        rule: DesktopSiteRule? = null,
        navigationUrl: String = "",
        onComplete: (() -> Unit)? = null
    ) {
        val currentGen = incrementGeneration(tabId)

        val isDesktop = (targetMode == DesktopMode.DESKTOP)
        val activeUrl = if (navigationUrl.isNotEmpty()) navigationUrl else webView.url.orEmpty()
        val effectiveHost = if (host.isNotEmpty()) host else try { android.net.Uri.parse(activeUrl).host.orEmpty() } catch (_: Exception) { "" }

        // Check if transition is already applied for this generation and mode
        val lastAppliedMode = tabAppliedModeMap[tabId]
        val lastTargetUrl = tabLastTargetUrlMap[tabId]

        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.Preparing)

        // 1. Capture Page State (scroll position, etc.) only for explicit user toggle / exception
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.CapturingPageState)
        if (reason == DesktopTransitionReason.USER_TOGGLE || reason == DesktopTransitionReason.SITE_EXCEPTION) {
            DesktopNavigationState.captureState(tabId, webView)
            pendingRestoreGenerations[tabId] = currentGen
        } else {
            pendingRestoreGenerations.remove(tabId)
            DesktopNavigationState.clearState(tabId)
        }

        // 2. Apply WebSettings
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.ApplyingWebSettings)
        DesktopWebViewConfigurator.configure(webView, effectiveHost, targetMode)

        // 3. Apply User Agent & UA Metadata
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.ApplyingUserAgent)
        val targetUA = UserAgentPolicy.resolveUserAgent(effectiveHost, targetMode, webView.context)
        if (webView.settings.userAgentString != targetUA) {
            webView.settings.userAgentString = targetUA
        }
        UserAgentMetadataPolicy.applyMetadata(webView, targetMode)
        DesktopDiagnostics.recordEvent(DesktopModeEvent.UserAgentApplied(effectiveHost, targetUA))

        // 4. Apply Viewport Policy
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.ApplyingViewport)
        DesktopViewportPolicy.apply(webView, targetMode)

        // 5. Apply Metrics
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.ApplyingMetrics)
        val metricsScript = DeviceMetricsManager.getMetricsScript(isDesktop)
        webView.post {
            if (isGenerationCurrent(tabId, currentGen)) {
                webView.evaluateJavascript(metricsScript, null)
            }
        }

        // 6. Apply Compatibility Rules (CSS)
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.ApplyingCompatibility)
        DesktopCssEnvironment.applyCompatibilityCss(webView, effectiveHost, isDesktop, rule)

        // 7. Single Navigation Decision (EXACTLY ONE action: loadUrl OR reload, NEVER BOTH)
        if (!isGenerationCurrent(tabId, currentGen)) return
        setTabState(tabId, DesktopModeTransitionState.Navigating)
        val resolvedTargetUrl = DesktopNavigationPolicy.resolveDesktopUrl(activeUrl, isDesktop)

        val normalizedCurrent = normalizeUrl(activeUrl)
        val normalizedTarget = normalizeUrl(resolvedTargetUrl)

        val needsUrlChange = resolvedTargetUrl.isNotEmpty() && normalizedTarget != normalizedCurrent

        // Guard against duplicate / stale transitions
        if (lastAppliedMode == targetMode && lastTargetUrl == resolvedTargetUrl && reason != DesktopTransitionReason.USER_TOGGLE) {
            if (isGenerationCurrent(tabId, currentGen)) {
                setTabState(tabId, DesktopModeTransitionState.Completed)
                onComplete?.invoke()
            }
            return
        }

        tabAppliedModeMap[tabId] = targetMode
        tabLastTargetUrlMap[tabId] = resolvedTargetUrl

        if (onComplete != null) {
            pendingCallbacks[tabId] = Pair(currentGen, onComplete)
        }

        var navigationInitiated = false
        if (needsUrlChange) {
            DesktopDiagnostics.recordEvent(DesktopModeEvent.UrlRewritten(activeUrl, resolvedTargetUrl))
            navigationInitiated = true
            webView.post {
                if (isGenerationCurrent(tabId, currentGen)) {
                    webView.loadUrl(resolvedTargetUrl)
                }
            }
        } else if (reason == DesktopTransitionReason.USER_TOGGLE || reason == DesktopTransitionReason.SITE_EXCEPTION) {
            navigationInitiated = true
            webView.post {
                if (isGenerationCurrent(tabId, currentGen)) {
                    webView.reload()
                }
            }
        }

        // If no navigation was triggered (e.g. passive NEW_NAVIGATION check where mode matches), complete immediately
        if (!navigationInitiated && isGenerationCurrent(tabId, currentGen)) {
            setTabState(tabId, DesktopModeTransitionState.Completed)
            DesktopDiagnostics.recordEvent(DesktopModeEvent.ModeToggled(effectiveHost, isDesktop))
            onComplete?.invoke()
            pendingCallbacks.remove(tabId)
        }
    }

    fun onPageFinished(tabId: String, url: String, webView: WebView) {
        val currentGen = getGeneration(tabId)
        if (currentGen <= 0L) return

        // Consume any pending restore queued for this tab at currentGen
        val pendingRestoreGen = pendingRestoreGenerations.remove(tabId)
        if (pendingRestoreGen != null && pendingRestoreGen == currentGen && isGenerationCurrent(tabId, currentGen)) {
            setTabState(tabId, DesktopModeTransitionState.RestoringPageState)
            DesktopNavigationState.restoreState(tabId, webView, currentGen)
        } else {
            // Clean up any stale state if no restore was pending
            DesktopNavigationState.clearState(tabId)
        }

        if (isGenerationCurrent(tabId, currentGen)) {
            setTabState(tabId, DesktopModeTransitionState.Completed)
            
            val pending = pendingCallbacks.remove(tabId)
            if (pending != null && pending.first == currentGen) {
                pending.second.invoke()
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        if (url.isEmpty()) return ""
        return try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path?.trimEnd('/').orEmpty()
            val query = uri.query.orEmpty()
            "$scheme://$host$path${if (query.isNotEmpty()) "?$query" else ""}"
        } catch (_: Exception) {
            url.trimEnd('/')
        }
    }
}
