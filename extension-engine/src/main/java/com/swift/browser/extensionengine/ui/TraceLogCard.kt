package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionDebugLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TraceLogCard(
    log: ExtensionDebugLog,
    modifier: Modifier = Modifier
) {
    val (severityColor, severityIcon) = when (log.severity) {
        "ERROR" -> Pair(Color(0xFFF87171), Icons.Default.Error)
        "WARNING" -> Pair(Color(0xFFFBBF24), Icons.Default.Warning)
        else -> Pair(Color(0xFF60A5FA), Icons.Default.Info)
    }
    val timeStr = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp)
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
                    Text("[${log.type.name}] ${log.extensionName}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    Text(timeStr, fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.message,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}
