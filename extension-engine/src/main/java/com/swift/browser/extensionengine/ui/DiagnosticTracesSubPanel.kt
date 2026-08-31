package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionDebuggerEngine

@Composable
fun DiagnosticTracesSubPanel(
    extensionId: String? = null,
    modifier: Modifier = Modifier
) {
    val debugger = remember { ExtensionDebuggerEngine.instance }
    val logs by debugger.logs.collectAsState()

    val filteredLogs = remember(logs, extensionId) {
        if (extensionId != null) logs.filter { it.extensionId == extensionId }
        else logs
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Captured Traces (${filteredLogs.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            TextButton(onClick = { debugger.clearLogs() }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No diagnostic traces recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    TraceLogCard(log = log)
                }
            }
        }
    }
}
