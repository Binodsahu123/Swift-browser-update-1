package com.swift.browser.notificationengine

import android.content.Context
import android.util.Log
import com.swift.browser.permissionengine.PermissionEngineProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only / cache adapter backed by canonical PermissionEngine authority.
 * Delegates all website permission queries and updates to permission-engine.
 */
class WebsitePermissionStore(private val context: Context) {
    private val db = NotificationDatabase.getDatabase(context)
    private val TAG = "WebsitePermissionStore"

    /**
     * Set subscription or permission state for a website.
     * Delegates permission to permission-engine and synchronizes subscription cache.
     */
    suspend fun setPermission(websiteUrl: String, websiteName: String, permission: String) = withContext(Dispatchers.IO) {
        val mappedState = when (permission.uppercase()) {
            "ALLOW", "ALLOW_ALWAYS" -> "ALLOW_ALWAYS"
            "ALLOW_ONCE" -> "ALLOW_ONCE"
            "BLOCK" -> "BLOCK"
            else -> "ASK"
        }

        try {
            val permissionEngine = PermissionEngineProvider.get(context)
            permissionEngine.setPermissionState(websiteUrl, "NOTIFICATIONS", mappedState)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing permission to PermissionEngine: ${e.message}", e)
        }

        val resolvedRss = NotificationRegistry.resolveRssUrl(websiteUrl)
        val existing = db.subscriptionDao().getSubscription(websiteUrl)
        val canonicalStatus = if (mappedState.startsWith("ALLOW")) "ALLOW" else if (mappedState == "BLOCK") "BLOCK" else "ASK"
        
        val newSub = if (existing != null) {
            existing.copy(
                permission = canonicalStatus,
                enabled = mappedState.startsWith("ALLOW"),
                customRssUrl = existing.customRssUrl ?: resolvedRss
            )
        } else {
            NotificationSubscription(
                websiteUrl = websiteUrl,
                websiteName = websiteName,
                permission = canonicalStatus,
                enabled = mappedState.startsWith("ALLOW"),
                customRssUrl = resolvedRss
            )
        }
        
        db.subscriptionDao().insertSubscription(newSub)
        Log.d(TAG, "Synchronized permission $canonicalStatus for website $websiteName ($websiteUrl) with feed $resolvedRss")
    }

    /**
     * Get permission state ('ALLOW', 'BLOCK', 'ASK') backed by canonical PermissionEngine authority.
     */
    suspend fun getPermission(websiteUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val permissionEngine = PermissionEngineProvider.get(context)
            val state = permissionEngine.getPermissionState(websiteUrl, "NOTIFICATIONS")
            return@withContext when (state.lowercase()) {
                "allow", "allow_always", "allow_once" -> "ALLOW"
                "block" -> "BLOCK"
                else -> "ASK"
            }
        } catch (e: Exception) {
            Log.w(TAG, "PermissionEngine lookup failed, falling back to subscription cache: ${e.message}")
            val sub = db.subscriptionDao().getSubscription(websiteUrl)
            return@withContext sub?.permission ?: "ASK"
        }
    }

    /**
     * Checks if notifications are allowed for a website by checking canonical PermissionEngine
     * authority and verifying subscription settings.
     */
    suspend fun isAllowed(websiteUrl: String): Boolean = withContext(Dispatchers.IO) {
        val perm = getPermission(websiteUrl)
        if (perm != "ALLOW") {
            return@withContext false
        }
        val sub = db.subscriptionDao().getSubscription(websiteUrl)
        if (sub != null) {
            return@withContext sub.enabled && (sub.pauseUntil < System.currentTimeMillis())
        }
        return@withContext true
    }
}
