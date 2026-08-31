package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.ParsedExtension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPopupBottomSheet(
    show: Boolean,
    extension: ParsedExtension?,
    popupUrl: String?,
    api: ExtensionEngineApi,
    onDismiss: () -> Unit
) {
    if (!show || extension == null || popupUrl == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ExtensionIconMapper.getIconForExtension(extension.id, extension.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = extension.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Popup")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Specialized Portal Routing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val extIdLower = extension.id.lowercase()
                val nameLower = extension.name.lowercase()

                when {
                    extIdLower.contains("metamask") || nameLower.contains("metamask") -> {
                        MetaMaskPopupPortal(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                    extIdLower.contains("grok_automation") || nameLower.contains("grok automation") -> {
                        GrokAutomationPopupPortal(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                    extIdLower.contains("dark_reader") || nameLower.contains("dark reader") -> {
                        DarkReaderPopupPortal(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                    extIdLower.contains("grok") || nameLower.contains("grok 4") -> {
                        Grok4AiPopupPortal(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                    extIdLower.contains("adblock") || extIdLower.contains("adshield") || nameLower.contains("adblock") -> {
                        AdBlockPopupPortal(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                    extIdLower.startsWith("custom_") -> {
                        CustomUploadedExtensionPopupPortal(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                    else -> {
                        GenericPopupView(extensionId = extension.id, popupUrl = popupUrl, api = api)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
