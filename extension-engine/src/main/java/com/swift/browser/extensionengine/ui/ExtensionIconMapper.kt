package com.swift.browser.extensionengine.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

object ExtensionIconMapper {

    fun getIconForExtension(extensionId: String, extensionName: String): ImageVector {
        val lowerId = extensionId.lowercase(Locale.ROOT)
        val lowerName = extensionName.lowercase(Locale.ROOT)

        return when {
            lowerId.contains("metamask") || lowerName.contains("metamask") || lowerId == "ext_metamask" -> Icons.Default.Wallet
            lowerId.contains("grok_4") || lowerName.contains("grok 4") || lowerId == "ext_grok_4" -> Icons.Default.SmartToy
            lowerId.contains("grok_automation") || lowerName.contains("grok auto") || lowerId == "ext_grok_automation" -> Icons.Default.ElectricBolt
            lowerId.contains("dark_reader") || lowerName.contains("dark reader") || lowerId == "ext_dark_reader" -> Icons.Default.Brightness4
            lowerId.contains("adblock") || lowerName.contains("adblock") || lowerName.contains("ublock") || lowerId == "ext_adblock" -> Icons.Default.Shield
            else -> Icons.Default.Extension
        }
    }
}
