package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionEngineApi

@Composable
fun DarkReaderPopupPortal(
    extensionId: String,
    popupUrl: String,
    api: ExtensionEngineApi,
    modifier: Modifier = Modifier
) {
    val uiState by api.uiState.collectAsState()
    val isEnabled = remember(uiState.enabledExtensions) {
        uiState.enabledExtensions.any { it.id == extensionId }
    }

    var brightness by remember { mutableFloatStateOf(100f) }
    var contrast by remember { mutableFloatStateOf(100f) }

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
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brightness4, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Dark Reader Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { api.setEnabled(extensionId, it) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Brightness: ${brightness.toInt()}%", fontSize = 12.sp)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 50f..150f
                )

                Text("Contrast: ${contrast.toInt()}%", fontSize = 12.sp)
                Slider(
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = 50f..150f
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GenericPopupView(extensionId = extensionId, popupUrl = popupUrl, api = api, modifier = Modifier.weight(1f))
    }
}
