package com.swift.browser.browserengine.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast

object ShortcutUtils {
    fun pinWebpageShortcut(
        context: Context,
        url: String,
        title: String,
        favicon: Bitmap? = null
    ) {
        if (url.isBlank()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage(context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val label = title.ifBlank { url }
                    val icon = if (favicon != null) {
                        Icon.createWithBitmap(favicon)
                    } else {
                        Icon.createWithResource(context, android.R.drawable.ic_menu_compass)
                    }
                    val shortcut = ShortcutInfo.Builder(context, "shortcut_${url.hashCode()}_${System.currentTimeMillis()}")
                        .setShortLabel(label.take(25))
                        .setLongLabel(label)
                        .setIcon(icon)
                        .setIntent(intent)
                        .build()
                    shortcutManager.requestPinShortcut(shortcut, null)
                    Toast.makeText(context, "Adding shortcut to home screen...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Home screen shortcuts not supported by launcher", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Shortcut pinning requires Android 8.0+", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to create shortcut: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
