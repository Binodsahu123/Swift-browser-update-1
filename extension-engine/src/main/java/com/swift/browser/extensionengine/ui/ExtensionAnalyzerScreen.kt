package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swift.browser.extensionengine.DebugErrorType
import com.swift.browser.extensionengine.ExtensionAnalysisReport
import com.swift.browser.extensionengine.ExtensionDebugLog
import com.swift.browser.extensionengine.ExtensionDebuggerEngine
import com.swift.browser.extensionengine.ExtensionEngineApi
import com.swift.browser.extensionengine.ParsedExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExtensionAnalyzerScreen(
    show: Boolean,
    api: ExtensionEngineApi,
    onDismiss: () -> Unit
) {
    if (!show) return

    val uiState by api.uiState.collectAsState()
    val installed = uiState.installedExtensions

    var selectedExtensionId by remember(installed) {
        mutableStateOf(installed.firstOrNull()?.id ?: "ext_dark_reader")
    }

    val debuggerEngine = remember { ExtensionDebuggerEngine.instance }
    val logs by debuggerEngine.logs.collectAsState()
    val report = remember(selectedExtensionId) {
        debuggerEngine.generateAnalysisReport(selectedExtensionId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Extension Deep Analyzer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Extension Deep Analyzer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Native Runtime Audit & CSP Diagnostic",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Extension Dropdown / Selector
                if (installed.isNotEmpty()) {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    val selectedExt = installed.find { it.id == selectedExtensionId } ?: installed.first()

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ExtensionIconMapper.getIconForExtension(selectedExt.id, selectedExt.name),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(selectedExt.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(selectedExt.id, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Extension")
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            installed.forEach { ext ->
                                DropdownMenuItem(
                                    text = { Text("${ext.name} (${ext.id})") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = ExtensionIconMapper.getIconForExtension(ext.id, ext.name),
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        selectedExtensionId = ext.id
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Scrollable Diagnostic Body
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Health Score Gauge Card
                    item {
                        HealthScoreCard(report = report)
                    }

                    // English Audit Section
                    item {
                        DiagnosticSectionCard(
                            title = "Technical Analysis (English)",
                            icon = Icons.Default.Description,
                            summary = report.errorSummaryEnglish,
                            solution = report.deepSolutionEnglish,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Hindi Audit Section
                    item {
                        DiagnosticSectionCard(
                            title = "विश्लेषण और समाधान (Hindi)",
                            icon = Icons.Default.Translate,
                            summary = report.errorSummaryHindi,
                            solution = report.deepSolutionHindi,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    }

                    // Security & Manifest Audit Warnings
                    if (report.manifestIssues.isNotEmpty() || report.securityWarnings.isNotEmpty()) {
                        item {
                            SecurityAuditCard(report = report)
                        }
                    }

                    // Live Runtime Logs Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Runtime Console Traces (${logs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { debuggerEngine.clearLogs() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Traces", fontSize = 12.sp)
                            }
                        }
                    }

                    // Runtime Logs List
                    val filteredLogs = logs.filter { it.extensionId == selectedExtensionId || logs.size <= 5 }
                    if (filteredLogs.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "No runtime logs captured for this extension.",
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredLogs, key = { it.id }) { log ->
                            LogItemRow(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthScoreCard(report: ExtensionAnalysisReport) {
    val scoreColor = when {
        report.healthScore >= 90 -> Color(0xFF4CAF50)
        report.healthScore >= 70 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "${report.healthScore}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = scoreColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Runtime Health Score",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = when {
                        report.healthScore >= 90 -> "Optimal performance. Manifest & API compliant."
                        report.healthScore >= 70 -> "Minor warnings detected. Declarative rule limits close to threshold."
                        else -> "Critical errors found. CSP inline scripts or bridge bindings blocked."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    summary: String,
    solution: String,
    containerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Audit Summary:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = summary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recommended Fix:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = solution,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecurityAuditCard(report: ExtensionAnalysisReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Security & Manifest Warnings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            report.manifestIssues.forEach { issue ->
                Text("• Manifest: $issue", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            report.securityWarnings.forEach { warning ->
                Text("• Permission: $warning", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LogItemRow(log: ExtensionDebugLog) {
    val (severityColor, severityIcon) = when (log.severity) {
        "ERROR" -> Pair(MaterialTheme.colorScheme.error, Icons.Default.Error)
        "WARNING" -> Pair(Color(0xFFFF9800), Icons.Default.Warning)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.Info)
    }
    val timeStr = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(severityIcon, contentDescription = null, tint = severityColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("[${log.type.name}] ${log.extensionName}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(timeStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.message,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
