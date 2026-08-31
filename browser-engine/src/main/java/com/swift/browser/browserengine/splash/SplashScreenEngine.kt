package com.swift.browser.browserengine.splash

import android.app.Activity
import android.content.Intent

class SplashScreenEngine(
    private val activity: Activity,
    private val firstLaunchManager: FirstLaunchManager
) {
    fun decideNextRoute(): SplashRoute {
        return if (firstLaunchManager.isFirstLaunch()) {
            SplashRoute.Onboarding
        } else {
            SplashRoute.Home
        }
    }

    fun navigateToHome() {
        // Compose host handles route transition directly without recreating MainActivity
    }
}

enum class SplashRoute {
    SplashOnly, Onboarding, Home
}
