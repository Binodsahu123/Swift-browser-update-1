package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionEngineApi

@Composable
fun CustomUploadedExtensionPopupPortal(
    extensionId: String,
    popupUrl: String,
    api: ExtensionEngineApi,
    modifier: Modifier = Modifier
) {
    val uiState by api.uiState.collectAsState()
    val ext = remember(uiState.installedExtensions, extensionId) {
        uiState.installedExtensions.find { it.id == extensionId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(ext?.name ?: "Custom Extension", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Version ${ext?.version ?: "1.0"} • Local Upload", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GenericPopupView(extensionId = extensionId, popupUrl = popupUrl, api = api, modifier = Modifier.weight(1f))
    }
}
