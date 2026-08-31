package com.swift.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val ContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD700),
    secondary = Color(0xFF00FF00),
    tertiary = Color(0xFF00FFFF),
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.Black
)

private val ContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF8B0000),
    secondary = Color(0xFF00008B),
    tertiary = Color(0xFF006400),
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onPrimary = Color.White
)

@Composable
fun MyApplicationTheme(
    appThemeMode: String = "System", // "System", "Light", "Dark", "Contrast"
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isCurrentlyDark = when (appThemeMode) {
        "Dark" -> true
        "Light" -> false
        "Contrast" -> darkTheme
        else -> darkTheme
    }

    val colorScheme = when (appThemeMode) {
        "Contrast" -> if (isCurrentlyDark) ContrastDarkColorScheme else ContrastLightColorScheme
        else -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isCurrentlyDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isCurrentlyDark -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
