package com.swift.browser.notificationengine

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.swift.browser.permissionengine.NotificationRequestAdapter
import com.swift.browser.permissionengine.NotificationRequestParams
import com.swift.browser.permissionengine.OriginNormalizer
import com.swift.browser.permissionengine.PermissionEngineApi
import com.swift.browser.permissionengine.PermissionEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Interface representing the primary public gateway wrapper for the Notification Module.
 */
interface NotificationEngine {
    fun initialize(context: Context)
    fun getJavascriptPolyfill(websiteUrl: String, callback: (String) -> Unit)
    fun clearPrivateSession(sessionId: String? = null)
}

/**
 * Production-ready implementation of the Web Notification interceptor, bridging Javascript to Native APIs.
 */
class NotificationEngineImpl(private val context: Context) : NotificationEngine {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TAG = "NotificationEngineImpl"

    override fun initialize(context: Context) {
        Log.i(TAG, "Initializing Notification Engine and Scheduling Background Sync Service")
        BackgroundNotificationService.startEngine(context)
    }

    override fun clearPrivateSession(sessionId: String?) {
        Log.i(TAG, "Clearing in-memory private notification history for session: $sessionId")
        NotificationHistoryManager(context).clearPrivateHistory(sessionId)
    }

    /**
     * Resolves and builds a custom Javascript code injection string to polyfill standard Notification API on the fly.
     */
    override fun getJavascriptPolyfill(websiteUrl: String, callback: (String) -> Unit) {
        scope.launch {
            val permissionState = try {
                PermissionEngineProvider.get(context).getPermissionState(websiteUrl, "NOTIFICATIONS")
            } catch (_: Exception) {
                "Ask"
            }
            val jsPermission = when (permissionState.lowercase()) {
                "allow", "allow_always", "allow_once" -> "granted"
                "block" -> "denied"
                else -> "default"
            }

            val script = """
                (function() {
                    // Check if already polyfilled to avoid duplicate overrides
                    if (window.Notification && window.Notification.isSwiftPolyfill) return;

                    var _activeRequests = {};

                    function SwiftNotification(title, options) {
                        this.title = title;
                        this.options = options || {};
                        
                        // Hand off notification request directly to Native Client Bridge only when granted
                        if (SwiftNotification.permission === 'granted') {
                            if (window.AndroidNotificationBridge) {
                                window.AndroidNotificationBridge.postNotification(
                                    title, 
                                    this.options.body || '', 
                                    window.location.href
                                );
                            }
                        }
                    }

                    SwiftNotification.isSwiftPolyfill = true;
                    SwiftNotification.permission = '$jsPermission';

                    SwiftNotification.requestPermission = function(callback) {
                        return new Promise(function(resolve, reject) {
                            if (!window.AndroidNotificationBridge) {
                                SwiftNotification.permission = 'default';
                                if (callback) callback('default');
                                resolve('default');
                                return;
                            }

                            // Check current canonical state first
                            var currentPerm = window.AndroidNotificationBridge.getSavedPermission(window.location.origin);
                            if (currentPerm === 'ALLOW') {
                                SwiftNotification.permission = 'granted';
                                if (callback) callback('granted');
                                resolve('granted');
                                return;
                            } else if (currentPerm === 'BLOCK') {
                                SwiftNotification.permission = 'denied';
                                if (callback) callback('denied');
                                resolve('denied');
                                return;
                            }

                            // Generate unique session request ID
                            var reqId = 'req_notif_' + Math.random().toString(36).substring(2, 10) + '_' + Date.now();
                            _activeRequests[reqId] = {
                                resolve: resolve,
                                callback: callback
                            };

                            // Send asynchronous request into Native Bridge (delegates to permission-engine)
                            window.AndroidNotificationBridge.requestPermission(
                                reqId,
                                window.location.origin,
                                document.title || '',
                                window.location.href
                            );
                        });
                    };

                    // Asynchronous callback receiver invoked from native Android looper
                    window.__swift_notification_onPermissionResponse = function(reqId, result) {
                        var webPerm = (result === 'ALLOW' || result === 'granted') ? 'granted' : 'denied';
                        SwiftNotification.permission = webPerm;
                        var pending = _activeRequests[reqId];
                        if (pending) {
                            delete _activeRequests[reqId];
                            if (pending.callback) {
                                try { pending.callback(webPerm); } catch (e) {}
                            }
                            if (pending.resolve) {
                                try { pending.resolve(webPerm); } catch (e) {}
                            }
                        }
                    };

                    // Overwrite the web API globally
                    window.Notification = SwiftNotification;
                    console.log('SwiftBrowser: WebView Notification API successfully polyfilled for ' + window.location.origin);
                })();
            """.trimIndent()
            callback(script)
        }
    }
}

/**
 * Native Bridge mapping for Javascript to trigger alerts and permission requests inside WebViews.
 * All permission prompts and decisions are handled exclusively by permission-engine.
 */
class AndroidNotificationBridge(
    private val context: Context,
    private val webView: WebView? = null,
    private val tabId: String = "active",
    private val isIncognito: Boolean = false
) {
    companion object {
        const val INTERFACE_NAME = "AndroidNotificationBridge"
        private const val TAG = "NotificationBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun requestPermission(requestId: String, origin: String, pageTitle: String, pageUrl: String) {
        Log.i(TAG, "Website requested notification permission: origin=$origin, reqId=$requestId, tabId=$tabId, incognito=$isIncognito")
        mainHandler.post {
            val currentUrl = webView?.url ?: pageUrl.ifBlank { origin }
            val normalizedJsOrigin = OriginNormalizer.normalize(origin)
            val normalizedCurrentOrigin = OriginNormalizer.normalize(currentUrl)

            val effectiveOrigin = if (normalizedJsOrigin.isNotBlank()) normalizedJsOrigin else normalizedCurrentOrigin
            val actualRequestId = if (requestId.isNotBlank()) requestId else "req_notif_" + UUID.randomUUID().toString().substring(0, 8)

            val params = NotificationRequestParams(
                origin = effectiveOrigin,
                pageUrl = currentUrl,
                title = pageTitle,
                tabId = tabId,
                userGesture = true,
                isIncognito = isIncognito,
                requestId = actualRequestId
            )

            // Delegate exclusively to PermissionEngineApi — single permission authority and single UI prompt
            PermissionEngineApi.handleNotificationRequest(context, params) { isAllowed ->
                Log.i(TAG, "Notification permission resolved for $effectiveOrigin: isAllowed=$isAllowed (reqId=$actualRequestId)")
                
                // If allowed, update website cache/subscription metadata in notification-engine
                if (isAllowed) {
                    val host = NotificationRegistry.getHostDomain(effectiveOrigin)
                    val websiteName = pageTitle.ifBlank { host }
                    val store = WebsitePermissionStore(context)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            store.setPermission(effectiveOrigin, websiteName, "ALLOW")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed updating subscription cache on allow: ${e.message}")
                        }
                    }
                }

                // Return result asynchronously to the exact requesting session in WebView
                mainHandler.post {
                    val jsResult = if (isAllowed) "granted" else "denied"
                    val jsCode = "if (window.__swift_notification_onPermissionResponse) { window.__swift_notification_onPermissionResponse('$actualRequestId', '$jsResult'); }"
                    webView?.evaluateJavascript(jsCode, null)
                }
            }
        }
    }

    @JavascriptInterface
    fun requestPermission(origin: String, pageTitle: String) {
        val reqId = "req_notif_" + UUID.randomUUID().toString().substring(0, 8)
        requestPermission(reqId, origin, pageTitle, origin)
    }

    @JavascriptInterface
    fun getSavedPermission(origin: String): String {
        return try {
            val permissionEngine = PermissionEngineProvider.get(context)
            val state = permissionEngine.getPermissionState(origin, "NOTIFICATIONS")
            when (state.lowercase()) {
                "allow", "allow_always", "allow_once" -> "ALLOW"
                "block" -> "BLOCK"
                else -> "ASK"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get permission: ${e.message}")
            "ASK"
        }
    }

    @JavascriptInterface
    fun postNotification(title: String, body: String, clickUrl: String) {
        Log.i(TAG, "Instant Notification Posted from WebView: $title ($clickUrl) [incognito=$isIncognito]")
        val host = NotificationRegistry.getHostDomain(clickUrl)
        val websiteName = host.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        showWebNotificationHelper(
            context = context,
            websiteUrl = clickUrl,
            websiteName = websiteName,
            title = title,
            body = body,
            clickUrl = clickUrl,
            contextMode = NotificationBrowsingContext(isPrivate = isIncognito)
        )
    }
}

fun showWebNotificationHelper(
    context: Context,
    websiteUrl: String,
    websiteName: String,
    title: String,
    body: String,
    clickUrl: String,
    contextMode: NotificationBrowsingContext = NotificationBrowsingContext.NORMAL
) {
    val repository = com.swift.browser.notificationengine.data.NotificationRepository(context)
    val historyManager = NotificationHistoryManager(context)
    val permissionStore = WebsitePermissionStore(context)
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val resolvedName = websiteName.ifEmpty { NotificationRegistry.getHostDomain(websiteUrl) }

        // 1. Record history: in-memory runtime only if private; persistent Room DB if normal
        historyManager.addHistoryItem(
            websiteUrl = websiteUrl,
            websiteName = resolvedName,
            title = title,
            body = body,
            clickUrl = clickUrl,
            browsingContext = contextMode
        )

        // 2. Verify canonical website permission + channel settings (delegates to PermissionEngine)
        if (!permissionStore.isAllowed(websiteUrl)) {
            Log.d("NotificationEngine", "Notification blocked for $websiteUrl by website permission or channel settings")
            return@launch
        }

        // 3. Verify Android system notification permission
        if (!NotificationPermissionManager.hasSystemPermission(context)) {
            Log.w("NotificationEngine", "Notification suppressed: Android POST_NOTIFICATIONS runtime permission not granted")
            return@launch
        }

        // 4. Notification channel configuration & system dispatch (DO NOT block notification delivery merely because browsing is private)
        NotificationChannelManager.createNotificationChannels(context)
        val sub = repository.getSubscription(websiteUrl)
        val channelId = NotificationChannelManager.getChannelId(
            priority = sub?.priority ?: 1,
            soundEnabled = sub?.soundEnabled ?: true,
            vibrationEnabled = sub?.vibrationEnabled ?: true,
            isMuted = sub?.isMuted ?: false
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(clickUrl)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notification_click_url", clickUrl)
            putExtra("NOTIFICATION_URL", clickUrl)
            if (contextMode.isPrivate) {
                putExtra("is_incognito", true)
            }
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            clickUrl.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("[$resolvedName] $title")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(clickUrl.hashCode(), builder.build())
    }
}
