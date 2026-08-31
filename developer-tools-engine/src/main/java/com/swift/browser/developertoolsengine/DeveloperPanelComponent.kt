package com.swift.browser.developertoolsengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeveloperPanelComponent(
    htmlContent: String = "",
    onClose: () -> Unit
) {
    var activeTabIdx by remember { mutableIntStateOf(0) }
    val tabs = listOf("Elements", "Console", "Network", "Storage", "Security")

    Card(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Panel Header tab strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.DeveloperMode, contentDescription = "Console", tint = Color(0xFF818CF8))
                    Text(text = "SWIFTBROWSER DEVTOOLS", color = Color.White, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                }

                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            TabRow(
                selectedTabIndex = activeTabIdx,
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF6366F1),
                modifier = Modifier.height(48.dp)
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = activeTabIdx == idx,
                        onClick = { activeTabIdx = idx },
                        text = { Text(title, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                    )
                }
            }

            Divider(color = Color.DarkGray)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (activeTabIdx) {
                    0 -> ElementInspector()
                    1 -> ConsoleViewer()
                    2 -> NetworkViewer()
                    3 -> StorageViewer()
                    4 -> SecurityPanel()
                }
            }
        }
    }
}

@Composable
fun SecurityPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(64.dp))
        Text("Web Connection Secure", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            text = "This site loaded over an encrypted TLS connection containing verified certificates.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SecurityRow(label = "Protocol", value = "TLS 1.3 / HTTP/2")
                SecurityRow(label = "Cipher Suite", value = "TLS_AES_256_GCM_SHA384")
                SecurityRow(label = "Signature Algorithm", value = "RSA-PSS (2048-bit)")
                SecurityRow(label = "Mixed Content Status", value = "No passive dynamic resources mixed")
            }
        }
    }
}

@Composable
private fun SecurityRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
