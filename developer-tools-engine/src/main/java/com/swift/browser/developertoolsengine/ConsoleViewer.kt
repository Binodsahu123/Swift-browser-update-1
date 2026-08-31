package com.swift.browser.developertoolsengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Save
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleViewer(
    engine: InspectorEngine = InspectorEngine.instance
) {
    val logs by engine.consoleLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, searchQuery, selectedLevelFilter) {
        logs.filter { log ->
            val matchesQuery = log.message.contains(searchQuery, ignoreCase = true)
            val matchesLevel = selectedLevelFilter == null || log.level == selectedLevelFilter
            matchesQuery && matchesLevel
        }.reversed()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter console...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            val context = LocalContext.current
            IconButton(
                onClick = {
                    val file = ConsoleEngine.instance.exportLogs(context, "txt")
                    if (file != null) {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Export Console Logs"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // If FileProvider is not set up, just show toast
                            android.widget.Toast.makeText(context, "Saved to ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(Icons.Default.Save, contentDescription = "Export Logs", tint = Color.White)
            }
            IconButton(
                onClick = { engine.clearConsole() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(Icons.Default.ClearAll, contentDescription = "Clear Console", tint = Color.White)
            }
        }

        // Filter chips
        ScrollableTabRow(
            selectedTabIndex = when (selectedLevelFilter) {
                null -> 0
                LogLevel.LOG -> 1
                LogLevel.INFO -> 2
                LogLevel.WARNING -> 3
                LogLevel.ERROR -> 4
                LogLevel.DEBUG -> 5
            },
            containerColor = Color(0xFF0F172A),
            edgePadding = 8.dp,
            modifier = Modifier.height(40.dp)
        ) {
            Tab(selected = selectedLevelFilter == null, onClick = { selectedLevelFilter = null }) {
                Text("ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
            }
            LogLevel.values().forEach { level ->
                Tab(selected = selectedLevelFilter == level, onClick = { selectedLevelFilter = level }) {
                    Text(level.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = getLevelColor(level), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        Divider(color = Color.DarkGray)

        Box(modifier = Modifier.weight(1f)) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No console logs captured", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs) { log ->
                            ConsoleLogItem(log)
                            Divider(color = Color(0xFF1E293B))
                        }
                    }
                }
            }
        }

        // Bottom Diagnostics / Stats bar
        var showDiagnostics by remember { mutableStateOf(false) }
        val report = remember(logs) { ConsoleEngine.instance.getDiagnosticsReport() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Logs: ${logs.size} | Rate: ${report["current_logs_rate_per_sec"]}/s",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                TextButton(
                    onClick = { showDiagnostics = !showDiagnostics },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = if (showDiagnostics) "Hide Metrics" else "Show Metrics",
                        fontSize = 11.sp,
                        color = Color(0xFF818CF8)
                    )
                }
            }

            if (showDiagnostics) {
                Spacer(modifier = Modifier.height(4.dp))
                val levelsMap = report["logs_by_level"] as? Map<*, *>
                Text(
                    text = "LOG: ${levelsMap?.get("log") ?: 0} | INFO: ${levelsMap?.get("info") ?: 0} | WARN: ${levelsMap?.get("warning") ?: 0} | ERR: ${levelsMap?.get("error") ?: 0} | DBG: ${levelsMap?.get("debug") ?: 0}\n" +
                           "Proc Latency: ${report["average_processing_time_us"]}µs | DB Size: ${report["last_database_file_bytes"]} bytes\n" +
                           "Allocation: ${report["heap_status"]}",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
fun ConsoleLogItem(log: ConsoleLog) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeStr = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }
    val levelColor = getLevelColor(log.level)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "[$timeStr]",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = log.level.name,
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = log.message,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

private fun getLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> Color(0xFF60A5FA)
    LogLevel.WARNING -> Color(0xFFFBBF24)
    LogLevel.ERROR -> Color(0xFFF87171)
    LogLevel.DEBUG -> Color(0xFF34D399)
    LogLevel.LOG -> Color.White
}
