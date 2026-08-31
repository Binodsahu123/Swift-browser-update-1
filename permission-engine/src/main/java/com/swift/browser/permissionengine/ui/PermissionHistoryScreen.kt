package com.swift.browser.permissionengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.permissionengine.PermissionDescriptorRegistry
import com.swift.browser.permissionengine.PermissionDiagnostics
import com.swift.browser.permissionengine.PermissionTraceModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHistoryScreen(
    onBack: () -> Unit
) {
    val tracesState = PermissionDiagnostics.traces.collectAsState()
    val eventsState = PermissionDiagnostics.events.collectAsState()
    
    val traces = tracesState.value
    val events = eventsState.value

    val timeFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission History", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("history_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (traces.isNotEmpty() || events.isNotEmpty()) {
                        IconButton(
                            onClick = { PermissionDiagnostics.clearAll() },
                            modifier = Modifier.testTag("history_clear_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = Modifier.testTag("permission_history_screen")
    ) { innerPadding ->
        if (traces.isEmpty() && events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "No events",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "History is Empty",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Real-time logs and capability transaction details will appear here as web requests occur.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (traces.isNotEmpty()) {
                    item {
                        Text(
                            text = "Capability Transaction Traces (${traces.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(traces, key = { it.traceId }) { trace ->
                        PermissionTraceCard(trace = trace, timeFormatter = timeFormatter)
                    }
                }

                if (events.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Engine Event Logs (${events.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    items(events, key = { it.eventId }) { event ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = event.stage,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (event.status == "SUCCESS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = timeFormatter.format(Date(event.timestamp)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                Text(
                                    text = event.reason,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (event.details.isNotEmpty()) {
                                    Text(
                                        text = event.details,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionTraceCard(
    trace: PermissionTraceModel,
    timeFormatter: SimpleDateFormat
) {
    val isSuccess = trace.finalResult == "ALLOW" || trace.status == "SUCCESS" || trace.finalResult == "GRANTED"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trace_card_${trace.requestId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${trace.requestId}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timeFormatter.format(Date(trace.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (trace.secureContext) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (trace.secureContext) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Text(
                    text = trace.origin,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayName = PermissionDescriptorRegistry.getDescriptor(trace.permissionType)?.displayName ?: trace.permissionType
                Text(
                    text = "Capability: $displayName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Decision: ${trace.finalResult}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Android: ${trace.androidPermissionState}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (trace.androidPermissionState == "GRANTED") Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                )
                if (trace.requestSource.isNotBlank()) {
                    Text(
                        text = "Source: ${trace.requestSource}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            if (trace.reason.isNotEmpty()) {
                Text(
                    text = "Reason: ${trace.reason}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
