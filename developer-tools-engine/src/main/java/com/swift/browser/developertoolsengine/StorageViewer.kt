package com.swift.browser.developertoolsengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StorageViewer(
    engine: InspectorEngine = InspectorEngine.instance
) {
    val entries by engine.storageEntries.collectAsState()
    var selectedType by remember { mutableStateOf("LocalStorage") }

    val filteredEntries = remember(entries, selectedType) {
        entries.filter { it.type.equals(selectedType, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Tab Row selector
        TabRow(
            selectedTabIndex = when (selectedType) {
                "LocalStorage" -> 0
                "SessionStorage" -> 1
                "Cookies" -> 2
                else -> 3
            },
            containerColor = Color(0xFF0F172A),
            contentColor = Color(0xFF6366F1)
        ) {
            Tab(selected = selectedType == "LocalStorage", onClick = { selectedType = "LocalStorage" }) {
                Text("Local", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(10.dp))
            }
            Tab(selected = selectedType == "SessionStorage", onClick = { selectedType = "SessionStorage" }) {
                Text("Session", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(10.dp))
            }
            Tab(selected = selectedType == "Cookies", onClick = { selectedType = "Cookies" }) {
                Text("Cookies", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(10.dp))
            }
            Tab(selected = selectedType == "IndexedDB", onClick = { selectedType = "IndexedDB" }) {
                Text("IndexedDB", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(10.dp))
            }
        }

        Divider(color = Color.DarkGray)

        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No $selectedType keys found/captured", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredEntries) { entry ->
                        StorageEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageEntryRow(entry: StorageEntry) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = entry.key,
                color = Color(0xFF818CF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.value,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
