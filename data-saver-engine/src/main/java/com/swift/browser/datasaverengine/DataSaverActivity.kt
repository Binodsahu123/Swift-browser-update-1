package com.swift.browser.datasaverengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DataSaverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DataSaverScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSaverScreen(onBack: () -> Unit) {
    var isEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Saver Engine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F172A))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Icon(Icons.Default.DataUsage, "Data", tint = if (isEnabled) Color(0xFF10B981) else Color.Gray, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Data Saver is ${if (isEnabled) "Active" else "Disabled"}", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Compresses images and blocks unnecessary scripts to save bandwidth.", color = Color.LightGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            
            Spacer(modifier = Modifier.height(32.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Data Saver", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Save up to 60% of data", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (isEnabled) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NetworkCheck, "Stats", tint = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Data Saved This Month", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("124 MB", fontSize = 36.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
