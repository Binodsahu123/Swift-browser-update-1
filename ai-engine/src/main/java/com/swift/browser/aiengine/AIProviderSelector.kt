package com.swift.browser.aiengine

import android.content.Context

object AIProviderSelector {
    
    fun isLoginRequiredForProvider(providerName: String, context: Context): Boolean {
        val provider = AIProviderManager.getProvider(providerName) ?: return true
        val settingsManager = AISettingsManager(context)
        
        // If guest mode is enabled globally and this provider supports guest access, then login is NOT required!
        if (settingsManager.guestModeEnabled && provider.isGuestSupported) {
            return false
        }
        
        // Otherwise, login is required if it's default or if user turned off guest mode
        return provider.isLoginRequiredByDefault
    }

    fun canUseProviderImmediately(providerName: String, context: Context): Boolean {
        val accountManager = AIAccountManager(context)
        if (accountManager.isLoggedIn(providerName)) {
            return true
        }
        
        // Or if guest mode is enabled
        return !isLoginRequiredForProvider(providerName, context)
    }

    /**
     * Finds the next provider that can be accessed immediately (either guest allowed or logged in).
     */
    fun findNextAvailableInstantProvider(currentFailed: String, context: Context): String? {
        val list = AIProviderManager.getProvidersList()
        val currentIndex = list.indexOfFirst { it.equals(currentFailed, ignoreCase = true) }
        val startIndex = if (currentIndex != -1) currentIndex + 1 else 0

        for (i in startIndex until list.size) {
            val candidate = list[i]
            if (canUseProviderImmediately(candidate, context)) {
                return candidate
            }
        }
        
        // If all else fails, check Gemini since it handles secure default fallbacks
        if (!currentFailed.equals("Gemini", ignoreCase = true)) {
            return "Gemini"
        }
        return null
    }
}
