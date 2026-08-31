package com.swift.browser.audioengine.online

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.swift.browser.audioengine.AudioPlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("StaticFieldLeak")
object OnlineMusicWebViewManager {
    private const val PREFS_NAME = "swift_browser_media_prefs"
    private const val KEY_LAST_URL = "last_online_music_url"
    private const val KEY_LAST_TITLE = "last_online_music_title"
    private const val KEY_FAV_PREFIX = "online_fav_"

    private var activeWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTitle = MutableStateFlow("Online Music")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _currentUrl = MutableStateFlow("https://soundcloud.com")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _hasActiveMedia = MutableStateFlow(false)
    val hasActiveMedia: StateFlow<Boolean> = _hasActiveMedia.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _onlineError = MutableStateFlow<String?>(null)
    val onlineError: StateFlow<String?> = _onlineError.asStateFlow()

    class AndroidBridge(private val context: Context) {
        @JavascriptInterface
        fun updateState(playing: Boolean, title: String?, currentTime: Double, duration: Double, url: String?) {
            mainHandler.post {
                updateMediaState(
                    context = context,
                    playing = playing,
                    title = title,
                    currentSec = currentTime,
                    durationSec = duration,
                    navigatedUrl = url
                )
            }
        }
    }

    @Synchronized
    fun getOrCreateWebView(context: Context): WebView {
        if (activeWebView == null) {
            val appCtx = context.applicationContext
            val webView = WebView(appCtx)
            setupWebViewSettings(webView, appCtx)
            restoreState(appCtx)
            val startUrl = _currentUrl.value.ifEmpty { OnlineMusicLocaleManager.getHomeUrl(appCtx) }
            webView.loadUrl(startUrl)
            activeWebView = webView
        }
        return activeWebView!!
    }

    fun attachWebView(view: WebView, context: Context) {
        (view.parent as? ViewGroup)?.removeView(view)
        this.activeWebView = view
        restoreState(context)
        setupWebViewSettings(view, context)
    }

    fun detachWebView(view: WebView) {
        if (this.activeWebView == view) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    private fun setupWebViewSettings(view: WebView, context: Context) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = com.swift.browser.desktopengine.useragent.UserAgentManager.getMobileUserAgent(context)
        }

        try {
            val cookieEngine = com.swift.browser.cookieengine.CookieEngineApi.getInstance(context)
            cookieEngine.setupNormalCookies(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        view.addJavascriptInterface(AndroidBridge(context), "AndroidBridge")
        view.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                if (request == null) return
                try {
                    val rawOrigin = request.origin?.toString()?.trim() ?: ""
                    val currentUrl = view.url?.trim() ?: ""
                    val actualOrigin = if (rawOrigin.isNotBlank()) rawOrigin else currentUrl

                    val deterministicTabId = "online_music_wv_${view.hashCode()}"
                    val reqId = "req_music_${System.currentTimeMillis()}_${(1000..9999).random()}"
                    val permContext = com.swift.browser.permissionengine.PermissionRequestContext(
                        requestId = reqId,
                        tabId = deterministicTabId,
                        origin = actualOrigin,
                        pageUrl = currentUrl.ifBlank { actualOrigin },
                        requestSource = "online_music_webview"
                    )
                    com.swift.browser.permissionengine.PermissionEngineApi.handleWebViewPermissionRequest(
                        context = permContext,
                        request = request,
                        androidContext = context
                    )
                } catch (e: Exception) {
                    Log.e("OnlineMusicWebView", "Error handling permission request", e)
                }
            }

            override fun onPermissionRequestCanceled(request: android.webkit.PermissionRequest?) {
                if (request == null) return
                com.swift.browser.permissionengine.PermissionEngineApi.handlePermissionRequestCanceled(
                    request = request
                )
            }

            override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                callback?.onCustomViewHidden()
            }

            override fun onHideCustomView() {}
        }

        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                _onlineError.value = null
                if (!url.isNullOrBlank()) {
                    _currentUrl.value = url
                    updateFavoriteStatus(context, url)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!url.isNullOrBlank()) {
                    _currentUrl.value = url
                    saveState(context)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    _currentUrl.value = url
                    updateFavoriteStatus(context, url)
                    saveState(context)
                }
                injectMediaControllerScript()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    _onlineError.value = error?.description?.toString() ?: "Network Connection Error"
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    _onlineError.value = "HTTP Error: ${errorResponse?.statusCode}"
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
    }

    fun injectMediaControllerScript() {
        mainHandler.post {
            val script = """
                (function() {
                    if (window.__swiftAudioObserverInjected) {
                        if (window.__checkMediaInterval) clearInterval(window.__checkMediaInterval);
                    }
                    window.__swiftAudioObserverInjected = true;

                    var lastMediaElem = null;
                    var lastPlayingState = null;
                    var lastTitleState = null;
                    var lastUrl = null;
                    var lastTime = -1;
                    var lastDuration = -1;
                    
                    var recoveryRetries = 0;
                    var maxRecoveryRetries = 3;
                    var wasPlayingBeforeHidden = false;

                    function extractTitle() {
                        var titleElem = document.querySelector('ytmusic-player-bar .title') ||
                                        document.querySelector('ytmusic-player-bar .byline') ||
                                        document.querySelector('h1.ytd-watch-metadata') ||
                                        document.querySelector('.ytp-title-link') ||
                                        document.querySelector('title');
                        var title = titleElem ? (titleElem.innerText || titleElem.textContent || '').trim() : '';
                        if (!title) {
                            title = document.title || '';
                        }
                        title = title.replace(' - Online Music', '').trim();
                        title = title.replace(' - Online Music', '').trim();
                        if (title === 'Online Music' || title === 'Online Music') {
                            title = 'Online Music';
                        }
                        return title;
                    }

                    function reportState() {
                        var media = document.querySelector('video') || document.querySelector('audio');
                        var title = extractTitle();
                        var currentUrl = window.location.href;

                        if (media) {
                            var isPlaying = !media.paused && !media.ended && media.readyState > 2;
                            var currentTime = media.currentTime || 0;
                            var duration = media.duration || 0;

                            if (media !== lastMediaElem) {
                                if (lastMediaElem) {
                                    lastMediaElem.removeEventListener('play', reportState);
                                    lastMediaElem.removeEventListener('playing', reportState);
                                    lastMediaElem.removeEventListener('pause', reportState);
                                    lastMediaElem.removeEventListener('ended', reportState);
                                    lastMediaElem.removeEventListener('timeupdate', reportState);
                                    lastMediaElem.removeEventListener('durationchange', reportState);
                                    lastMediaElem.removeEventListener('loadedmetadata', reportState);
                                    lastMediaElem.removeEventListener('canplay', reportState);
                                }
                                lastMediaElem = media;
                                media.addEventListener('play', reportState, true);
                                media.addEventListener('playing', reportState, true);
                                media.addEventListener('pause', reportState, true);
                                media.addEventListener('ended', reportState, true);
                                media.addEventListener('timeupdate', reportState, true);
                                media.addEventListener('durationchange', reportState, true);
                                media.addEventListener('loadedmetadata', reportState, true);
                                media.addEventListener('canplay', reportState, true);
                            }

                            var diffTime = Math.abs(lastTime - currentTime);
                            if (isPlaying !== lastPlayingState || title !== lastTitleState || currentUrl !== lastUrl || diffTime >= 0.5 || duration !== lastDuration) {
                                lastPlayingState = isPlaying;
                                lastTitleState = title;
                                lastUrl = currentUrl;
                                lastTime = currentTime;
                                lastDuration = duration;
                                if (window.AndroidBridge && window.AndroidBridge.updateState) {
                                    window.AndroidBridge.updateState(isPlaying, title, currentTime, duration, currentUrl);
                                }
                            }
                        }
                    }

                    function autoSkipAds() {
                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-slot, .ytp-ad-overlay-close-button');
                        if (skipBtn) skipBtn.click();
                        var promoDismiss = document.querySelector('ytmusic-mealbar-promo-renderer-dismiss, ytmusic-dialog-renderer button, .ytmusic-mealbar-promo-renderer button');
                        if (promoDismiss) promoDismiss.click();
                    }

                    window.__checkMediaInterval = setInterval(function() {
                        reportState();
                        autoSkipAds();
                        
                        // Background recovery
                        if (document.hidden && wasPlayingBeforeHidden) {
                            var media = document.querySelector('video') || document.querySelector('audio');
                            if (media && media.paused && !media.ended && recoveryRetries < maxRecoveryRetries) {
                                recoveryRetries++;
                                media.play().catch(function(e){});
                            }
                        }
                    }, 800);

                    document.addEventListener('visibilitychange', function() {
                        var media = document.querySelector('video') || document.querySelector('audio');
                        if (document.hidden) {
                            if (media && !media.paused) {
                                wasPlayingBeforeHidden = true;
                                setTimeout(function() {
                                    if (media.paused && !media.ended && recoveryRetries < maxRecoveryRetries) {
                                        recoveryRetries++;
                                        media.play().catch(function(e){});
                                    }
                                }, 200);
                            }
                        } else {
                            wasPlayingBeforeHidden = false;
                            recoveryRetries = 0;
                        }
                    }, true);

                    reportState();
                })();
            """.trimIndent()
            activeWebView?.evaluateJavascript(script, null)
        }
    }

    fun updateMediaState(
        context: Context,
        playing: Boolean,
        title: String? = null,
        currentSec: Double = 0.0,
        durationSec: Double = 0.0,
        navigatedUrl: String? = null
    ) {
        _isPlaying.value = playing
        _hasActiveMedia.value = true
        _currentTimeMs.value = (currentSec * 1000).toLong()
        _durationMs.value = (durationSec * 1000).toLong()

        if (!navigatedUrl.isNullOrBlank() && navigatedUrl != _currentUrl.value) {
            _currentUrl.value = navigatedUrl
            updateFavoriteStatus(context, navigatedUrl)
        }

        if (!title.isNullOrBlank() && title != "Online Music") {
            _currentTitle.value = title
        }

        if (playing) {
            saveState(context)
        }
    }

    fun loadHome(context: Context? = null) {
        _currentUrl.value = ""
        val homeUrl = OnlineMusicLocaleManager.getHomeUrl(context)
        _currentUrl.value = homeUrl
        mainHandler.post {
            val view = context?.let { getOrCreateWebView(it) } ?: activeWebView
            view?.loadUrl(homeUrl)
        }
    }

    fun search(query: String, context: Context? = null) {
        val searchUrl = OnlineMusicLocaleManager.getSearchUrl(query, context)
        _currentUrl.value = searchUrl
        mainHandler.post {
            val view = context?.let { getOrCreateWebView(it) } ?: activeWebView
            view?.loadUrl(searchUrl)
        }
    }

    fun goBack(): Boolean {
        if (activeWebView?.canGoBack() == true) {
            mainHandler.post { activeWebView?.goBack() }
            return true
        }
        return false
    }

    fun canGoBack(): Boolean = activeWebView?.canGoBack() == true

    fun reload() {
        mainHandler.post { activeWebView?.reload() }
    }

    fun prewarm(context: Context) {
        getOrCreateWebView(context)
    }

    fun togglePlayPause() {
        mainHandler.post {
            val script = """
                (function() {
                    var v = document.querySelector('video') || document.querySelector('audio');
                    if (v) {
                        if (v.paused) v.play(); else v.pause();
                    } else {
                        var playBtn = document.querySelector('#play-pause-button, .play-pause-button, .ytp-play-button');
                        if (playBtn) playBtn.click();
                    }
                })();
            """.trimIndent()
            activeWebView?.evaluateJavascript(script, null)
        }
    }

    fun play() {
        mainHandler.post {
            activeWebView?.evaluateJavascript(
                "var v = document.querySelector('video') || document.querySelector('audio'); if (v && v.paused) v.play();",
                null
            )
        }
    }

    fun pause() {
        mainHandler.post {
            activeWebView?.evaluateJavascript(
                "var v = document.querySelector('video') || document.querySelector('audio'); if (v && !v.paused) v.pause();",
                null
            )
        }
    }

    fun next() {
        mainHandler.post {
            val script = """
                (function() {
                    var nextBtn = document.querySelector('.next-button, .ytp-next-button, #next-button, [aria-label="Next song"], ytmusic-player-bar .next-button');
                    if (nextBtn) nextBtn.click();
                })();
            """.trimIndent()
            activeWebView?.evaluateJavascript(script, null)
        }
    }

    fun previous() {
        mainHandler.post {
            val script = """
                (function() {
                    var prevBtn = document.querySelector('.previous-button, .ytp-prev-button, #previous-button, [aria-label="Previous song"], ytmusic-player-bar .previous-button');
                    if (prevBtn) prevBtn.click();
                })();
            """.trimIndent()
            activeWebView?.evaluateJavascript(script, null)
        }
    }

    fun seekTo(positionMs: Long) {
        val positionSec = positionMs / 1000.0
        mainHandler.post {
            val script = """
                (function() {
                    var v = document.querySelector('video') || document.querySelector('audio');
                    if (v) {
                        v.currentTime = $positionSec;
                    }
                })();
            """.trimIndent()
            activeWebView?.evaluateJavascript(script, null)
        }
    }

    fun toggleFavorite(context: Context) {
        val url = _currentUrl.value
        val prefs = getPrefs(context)
        val hashKey = KEY_FAV_PREFIX + url.hashCode()
        val rawKey = KEY_FAV_PREFIX + url
        val isFav = prefs.getBoolean(hashKey, false) || prefs.getBoolean(rawKey, false)

        val newFavState = !isFav
        prefs.edit()
            .putBoolean(hashKey, newFavState)
            .putBoolean(rawKey, newFavState)
            .apply()
        _isFavorite.value = newFavState
    }

    fun updateFavoriteStatus(context: Context, url: String) {
        val prefs = getPrefs(context)
        val hashKey = KEY_FAV_PREFIX + url.hashCode()
        val rawKey = KEY_FAV_PREFIX + url
        _isFavorite.value = prefs.getBoolean(hashKey, false) || prefs.getBoolean(rawKey, false)
        _currentUrl.value = url
    }

    private fun saveState(context: Context) {
        getPrefs(context).edit()
            .putString(KEY_LAST_URL, _currentUrl.value)
            .putString(KEY_LAST_TITLE, _currentTitle.value)
            .apply()
    }

    private fun restoreState(context: Context) {
        val prefs = getPrefs(context)
        val lastUrl = prefs.getString(KEY_LAST_URL, null)
        val lastTitle = prefs.getString(KEY_LAST_TITLE, "Online Music")
        if (!lastUrl.isNullOrBlank()) {
            _currentUrl.value = lastUrl
            _currentTitle.value = lastTitle ?: "Online Music"
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
