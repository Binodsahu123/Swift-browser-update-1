package com.swift.browser.uicore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dedicated Theme Mode model supporting Light, Dark, Contrast modes,
 * and automatic system theme detection.
 */
enum class AppThemeMode(
    val id: String,
    val displayName: String,
    val description: String
) {
    SYSTEM(
        id = "System",
        displayName = "System Default",
        description = "Automatically adapt to your device's system appearance (Light / Dark)"
    ),
    LIGHT(
        id = "Light",
        displayName = "Light Mode",
        description = "Crisp, bright aesthetic designed for daylight clarity"
    ),
    DARK(
        id = "Dark",
        displayName = "Dark Mode",
        description = "Deep slate & charcoal styling designed for night-time comfort"
    ),
    CONTRAST(
        id = "Contrast",
        displayName = "Contrast Mode",
        description = "High-contrast color profiles for maximum legibility and accessibility"
    );

    companion object {
        fun fromString(value: String): AppThemeMode {
            return entries.find { it.id.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: SYSTEM
        }
    }
}

/**
 * Resolves whether the current theme mode evaluates to dark styling.
 */
@Composable
fun isAppInDarkTheme(themeMode: AppThemeMode): Boolean {
    return when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.CONTRAST -> isSystemInDarkTheme()
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}
