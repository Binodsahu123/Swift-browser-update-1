package com.swift.browser.translateengine

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TranslationCompletedEvent(
    val tabId: String,
    val webView: WebView,
    val url: String,
    val langCode: String,
    val langName: String,
    val nodeCount: Int
)

data class TranslationUiState(
    val isVisible: Boolean = false,
    val isPageTranslated: Boolean = false,
    val detectedLanguage: String = "en",
    val targetLanguageName: String = "English",
    val targetLanguageCode: String = "en",
    val currentHost: String = "",
    val translationState: TranslationState = TranslationState.Hidden
)

interface TranslateEngineApi {
    val translateManager: TranslateManager
    val repository: TranslationRepository
    val settings: TranslateSettings
    val languageManager: TranslateLanguageManager
    val stateManager: TranslationStateManager
    val progressManager: TranslationProgressManager
    val debugger: TranslationDebugger
    val translationCompletedEvents: SharedFlow<TranslationCompletedEvent>
    val uiState: kotlinx.coroutines.flow.StateFlow<TranslationUiState>

    fun showBar()
    fun dismissBar()
    fun setTargetLanguage(langCode: String, langName: String)

    fun triggerTranslationSelection(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?
    ): Boolean

    fun dismissTranslateBar(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?
    )

    fun undoTranslation(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?
    )

    fun translateActivePage(
        targetLangCode: String,
        webView: WebView?,
        tabId: String?,
        currentUrl: String?,
        isDesktop: Boolean,
        onFinished: (Int) -> Unit = {}
    )

    fun executeGoogleTranslation(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?,
        langCode: String,
        langName: String,
        isDesktop: Boolean,
        onFinished: (Int) -> Unit = {}
    )

    suspend fun onPageStarted(
        context: Context,
        tabId: String,
        webView: WebView?,
        url: String?,
        isDesktop: Boolean,
        currentTranslateTargetCode: String,
        onAutoTranslateRequested: (langCode: String, langName: String) -> Unit
    )

    fun detectPageRenderContext(
        webView: WebView?,
        isDesktopMode: Boolean,
        callback: (PageRenderContext) -> Unit
    )

    companion object {
        @Volatile
        private var instance: TranslateEngineApi? = null

        fun getInstance(context: Context): TranslateEngineApi {
            return instance ?: synchronized(this) {
                instance ?: TranslateEngineApiImpl(context.applicationContext).also { instance = it }
            }
        }
    }
}

class TranslateEngineApiImpl(private val context: Context) : TranslateEngineApi {
    override val repository: TranslationRepository by lazy { TranslationRepository(context) }
    override val translateManager: TranslateManager by lazy { TranslateManager(context) }
    override val settings: TranslateSettings get() = translateManager.settings
    override val languageManager: TranslateLanguageManager get() = translateManager.languageManager
    override val stateManager: TranslationStateManager get() = translateManager.stateManager
    override val progressManager: TranslationProgressManager get() = translateManager.progressManager
    override val debugger: TranslationDebugger get() = translateManager.debugger

    private val _translationCompletedEvents = MutableSharedFlow<TranslationCompletedEvent>(extraBufferCapacity = 16)
    override val translationCompletedEvents: SharedFlow<TranslationCompletedEvent> = _translationCompletedEvents.asSharedFlow()

    private val initialTargetCode = TranslationPreferenceManager.getTargetLanguageCode(context)
    private val initialTargetName = TranslationPreferenceManager.getTargetLanguageName(context)

    private val _uiState = MutableStateFlow(
        TranslationUiState(
            targetLanguageCode = initialTargetCode,
            targetLanguageName = initialTargetName,
            translationState = TranslationState.Hidden
        )
    )
    override val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Main).launch {
            stateManager.currentState.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        isVisible = state != TranslationState.Hidden,
                        isPageTranslated = state == TranslationState.Translated,
                        translationState = state
                    )
                }
            }
        }
    }

    override fun showBar() {
        stateManager.transitionTo(TranslationState.Visible)
    }

    override fun dismissBar() {
        stateManager.transitionTo(TranslationState.Hidden)
    }

    override fun setTargetLanguage(langCode: String, langName: String) {
        TranslationPreferenceManager.saveTargetLanguage(context, langCode, langName)
        settings.setDefaultTargetLanguage(langCode)
        _uiState.update {
            it.copy(
                targetLanguageCode = langCode,
                targetLanguageName = langName
            )
        }
    }

    private fun extractHost(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    override fun triggerTranslationSelection(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?
    ): Boolean {
        if (currentUrl.isNullOrEmpty() ||
            currentUrl.startsWith("swift://") ||
            currentUrl.startsWith("orion://") ||
            currentUrl.startsWith("about:")
        ) {
            return false
        }

        val host = extractHost(currentUrl)
        _uiState.update { it.copy(currentHost = host) }
        stateManager.transitionTo(TranslationState.Visible)
        return true
    }

    override fun dismissTranslateBar(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?
    ) {
        val host = extractHost(currentUrl)
        if (host.isNotEmpty()) {
            TranslationSessionManager.disableDomainTranslation(host)
            TranslationPreferenceManager.removePersistedActiveDomain(context, host)
        }
        stateManager.transitionTo(TranslationState.Hidden)
        if (webView != null) {
            DomRestoreEngine.restoreOriginal(webView, tabId) { result ->
                android.util.Log.d("TranslateEngineApi", "DOM restoration on dismiss finished! Result: $result")
            }
        }
    }

    override fun undoTranslation(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?
    ) {
        val host = extractHost(currentUrl)
        if (host.isNotEmpty()) {
            TranslationSessionManager.disableDomainTranslation(host)
            TranslationPreferenceManager.removePersistedActiveDomain(context, host)
        }
        stateManager.transitionTo(TranslationState.Original)
        if (webView != null) {
            DomRestoreEngine.restoreOriginal(webView, tabId) { result ->
                android.util.Log.d("TranslateEngineApi", "DOM restoration finished! Result: $result")
            }
        }
    }

    override fun translateActivePage(
        targetLangCode: String,
        webView: WebView?,
        tabId: String?,
        currentUrl: String?,
        isDesktop: Boolean,
        onFinished: (Int) -> Unit
    ) {
        val langName = languageManager.getLanguageDisplayName(targetLangCode)
        executeGoogleTranslation(
            webView = webView,
            tabId = tabId,
            currentUrl = currentUrl,
            langCode = targetLangCode,
            langName = langName,
            isDesktop = isDesktop,
            onFinished = onFinished
        )
    }

    override fun executeGoogleTranslation(
        webView: WebView?,
        tabId: String?,
        currentUrl: String?,
        langCode: String,
        langName: String,
        isDesktop: Boolean,
        onFinished: (Int) -> Unit
    ) {
        if (webView == null || tabId.isNullOrEmpty() || currentUrl.isNullOrEmpty() ||
            currentUrl.startsWith("swift://") ||
            currentUrl.startsWith("orion://") ||
            currentUrl.startsWith("about:")
        ) {
            return
        }

        TranslationPreferenceManager.saveTargetLanguage(context, langCode, langName)
        val host = extractHost(currentUrl)
        if (host.isNotEmpty()) {
            TranslationSessionManager.startSession(tabId, host, langCode, langName)
            TranslationPreferenceManager.addPersistedActiveDomain(context, host)
        }

        stateManager.transitionTo(TranslationState.Translating)

        translateManager.translateWebView(webView, langCode, tabId, isDesktop) { count ->
            android.util.Log.d("TranslateEngineApi", "Page translation finished! Injected $count nodes.")
            stateManager.transitionTo(TranslationState.Translated)
            _translationCompletedEvents.tryEmit(
                TranslationCompletedEvent(
                    tabId = tabId,
                    webView = webView,
                    url = currentUrl,
                    langCode = langCode,
                    langName = langName,
                    nodeCount = count
                )
            )
            onFinished(count)
        }
    }

    override suspend fun onPageStarted(
        context: Context,
        tabId: String,
        webView: WebView?,
        url: String?,
        isDesktop: Boolean,
        currentTranslateTargetCode: String,
        onAutoTranslateRequested: (langCode: String, langName: String) -> Unit
    ) {
        if (url == null ||
            url.contains("translate.google.com") ||
            url.startsWith("swift://") ||
            url.startsWith("orion://") ||
            url.startsWith("about:") ||
            url == "about:blank"
        ) {
            return
        }

        val host = extractHost(url)
        TranslationNavigationManager.handlePageStarted(context, tabId, url)
        OriginalPageSnapshotManager.clearSnapshot(tabId)

        val autoTranslateLangCode = TranslationNavigationManager.shouldAutoTranslate(context, url)
        if (autoTranslateLangCode != null) {
            val langName = languageManager.getLanguageDisplayName(autoTranslateLangCode)
            onAutoTranslateRequested(autoTranslateLangCode, langName)
            return
        }

        // Sampling for language detection offer / policy-based auto translation
        val detectionScript = """
            (function() {
                try {
                    return (document.title || '') + ' ' + (document.body ? document.body.innerText.substring(0, 300) : '');
                } catch(e) {
                    return '';
                }
            })()
        """.trimIndent()

        webView?.post {
            webView.evaluateJavascript(detectionScript) { innerText ->
                if (!innerText.isNullOrBlank() && innerText != "null" && innerText != "\"\"") {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val detectedLang = translateManager.detectPageLanguage(innerText)
                            val isNeverSite = settings.getNeverTranslateSites().contains(host)
                            val isNeverLang = settings.getNeverTranslateLanguages().contains(detectedLang)

                            if (detectedLang.isNotEmpty() && detectedLang != "unknown" && detectedLang != currentTranslateTargetCode) {
                                val autoTranslate = (settings.isAutoTranslateEnabled() || settings.getAlwaysTranslateSites().contains(host)) &&
                                        !isNeverSite && !isNeverLang

                                if (autoTranslate) {
                                    val targetLangName = languageManager.getLanguageDisplayName(currentTranslateTargetCode)
                                    withContext(Dispatchers.Main) {
                                        onAutoTranslateRequested(currentTranslateTargetCode, targetLangName)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TranslateEngineApi", "Error in page translation detection", e)
                        }
                    }
                }
            }
        }
    }

    override fun detectPageRenderContext(
        webView: WebView?,
        isDesktopMode: Boolean,
        callback: (PageRenderContext) -> Unit
    ) {
        if (webView == null) {
            val fallbackProfile = if (isDesktopMode) TranslationPresentationProfile.COMPACT_DESKTOP else TranslationPresentationProfile.COMPACT_MOBILE
            callback(PageRenderContext(isDesktopModeRequested = isDesktopMode, profile = fallbackProfile))
            return
        }

        val renderContextScript = """
            (function() {
                try {
                    return JSON.stringify({
                        innerWidth: window.innerWidth || 0,
                        innerHeight: window.innerHeight || 0,
                        clientWidth: (document.documentElement && document.documentElement.clientWidth) || 0,
                        clientHeight: (document.documentElement && document.documentElement.clientHeight) || 0,
                        devicePixelRatio: window.devicePixelRatio || 1
                    });
                } catch(e) {
                    return '{}';
                }
            })()
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(renderContextScript) { jsonStr ->
                try {
                    val cleanJson = if (jsonStr != null && jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                        org.json.JSONObject(jsonStr.substring(1, jsonStr.length - 1).replace("\\\"", "\""))
                    } else if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                        org.json.JSONObject(jsonStr)
                    } else {
                        org.json.JSONObject()
                    }

                    val innerW = cleanJson.optInt("innerWidth", 0)
                    val innerH = cleanJson.optInt("innerHeight", 0)
                    val clientW = cleanJson.optInt("clientWidth", 0)
                    val clientH = cleanJson.optInt("clientHeight", 0)
                    val dpr = cleanJson.optDouble("devicePixelRatio", 1.0).toFloat()

                    val effectiveWidth = if (clientW > 0) clientW else innerW
                    val profile = when {
                        effectiveWidth >= 900 -> TranslationPresentationProfile.COMPACT_DESKTOP
                        effectiveWidth in 600..899 -> TranslationPresentationProfile.TABLET_COMPACT
                        isDesktopMode -> TranslationPresentationProfile.COMPACT_DESKTOP
                        else -> TranslationPresentationProfile.COMPACT_MOBILE
                    }

                    callback(
                        PageRenderContext(
                            innerWidth = innerW,
                            innerHeight = innerH,
                            clientWidth = clientW,
                            clientHeight = clientH,
                            devicePixelRatio = dpr,
                            isDesktopModeRequested = isDesktopMode,
                            profile = profile
                        )
                    )
                } catch (e: Exception) {
                    val fallbackProfile = if (isDesktopMode) TranslationPresentationProfile.COMPACT_DESKTOP else TranslationPresentationProfile.COMPACT_MOBILE
                    callback(PageRenderContext(isDesktopModeRequested = isDesktopMode, profile = fallbackProfile))
                }
            }
        }
    }
}
