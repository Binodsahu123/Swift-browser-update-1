package com.swift.browser.adblockengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swift.browser.adblockengine.AdProtectionEngineApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockImportExportScreen(
    adProtectionApi: AdProtectionEngineApi,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by adProtectionApi.uiState.collectAsState()
    var rawInputText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import / Export Filters") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Import Custom Filter Rules",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Paste EasyList/AdGuard format custom rules (one per line).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        label = { Text("Custom rules") },
                        placeholder = { Text("||example.com/ad.js\n! Custom rule comment") }
                    )

                    Button(
                        onClick = {
                            val lines = rawInputText.lines().map { it.trim() }.filter { it.isNotBlank() }
                            var count = 0
                            lines.forEach { line ->
                                if (line.startsWith("||")) {
                                    val domain = line.removePrefix("||").takeWhile { it != '/' && it != '^' }
                                    if (domain.isNotBlank()) {
                                        adProtectionApi.addBlockedSite(domain)
                                        count++
                                    }
                                }
                            }
                            statusMessage = "Imported $count custom domain rules successfully."
                            rawInputText = ""
                        },
                        enabled = rawInputText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Rules")
                    }

                    statusMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Export Active Rules",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val exportText = remember(uiState.adblockBlacklist, uiState.adblockWhitelist) {
                        val sb = StringBuilder()
                        sb.append("! Swift Browser AdBlock Filter Rules Export\n")
                        sb.append("! Whitelisted Sites:\n")
                        uiState.adblockWhitelist.forEach { sb.append("@@||$it^\n") }
                        sb.append("! Custom Blocked Sites:\n")
                        uiState.adblockBlacklist.forEach { sb.append("||$it^\n") }
                        sb.toString()
                    }

                    OutlinedTextField(
                        value = exportText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        label = { Text("Exported rules format") }
                    )
                }
            }
        }
    }
}
