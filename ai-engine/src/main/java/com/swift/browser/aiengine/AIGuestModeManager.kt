package com.swift.browser.aiengine
import com.swift.browser.aiengine.AIProviderManager
import com.swift.browser.aiengine.AISettingsManager


import android.content.Context

class AIGuestModeManager(context: Context) {
    private val settingsManager = AISettingsManager(context)
    private val accountManager = AIAccountManager(context)

    /**
     * Determines whether the selected provider can be accessed right now
     * as a Guest without showing any login prompts.
     */
    fun isGuestAllowed(providerName: String): Boolean {
        val provider = AIProviderManager.getProvider(providerName) ?: return false
        
        // 1. Check if the provider itself supports guest mode technically
        val supportsGuest = provider.isGuestSupported
        
        // 2. Check if the user has enabled Guest Mode globally in preferences
        val isGuestModeConfigured = settingsManager.guestModeEnabled

        return supportsGuest && isGuestModeConfigured
    }

    /**
     * Complete access check: returns TRUE if user is either logged in,
     * OR if guest mode is available and allowed.
     */
    fun canAccessProvider(providerName: String): Boolean {
        if (accountManager.isLoggedIn(providerName)) {
            return true
        }
        return isGuestAllowed(providerName)
    }
}
