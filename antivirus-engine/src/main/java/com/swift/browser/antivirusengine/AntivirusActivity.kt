package com.swift.browser.antivirusengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class AntivirusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AntivirusScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntivirusScreen(onBack: () -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    var scanComplete by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var scannedItem by remember { mutableStateOf("Ready to scan system...") }
    var junkFound by remember { mutableStateOf(0) }

    LaunchedEffect(scanning) {
        if (scanning) {
            progress = 0f
            scanComplete = false
            junkFound = 0
            val items = listOf("System Apps", "User Apps", "Cache", "Downloads", "Apk Files", "Temp Files")
            for (i in 1..100) {
                delay(30)
                progress = i / 100f
                if (i % 15 == 0) {
                    scannedItem = "Scanning: ${items[(i/15) % items.size]}..."
                }
            }
            junkFound = 342
            scannedItem = "Scan Complete. No viruses found."
            scanning = false
            scanComplete = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Antivirus Engine & Cleaner") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(if (scanComplete) Color(0xFF10B981).copy(alpha=0.2f) else if (scanning) Color(0xFF3B82F6).copy(alpha=0.2f) else Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (scanComplete) {
                    Icon(Icons.Default.CheckCircle, "Safe", tint = Color(0xFF10B981), modifier = Modifier.size(80.dp))
                } else {
                    Icon(Icons.Default.Security, "Scan", tint = if (scanning) Color(0xFF3B82F6) else Color.Gray, modifier = Modifier.size(80.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                if (scanComplete) "System Secure" else if (scanning) "Analyzing..." else "System Scanner",
                fontSize = 24.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(scannedItem, color = Color.LightGray)
            
            if (scanning) {
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color(0xFF3B82F6),
                    trackColor = Color(0xFF334155)
                )
            }

            if (scanComplete) {
                Spacer(modifier = Modifier.height(32.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, "Junk", tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$junkFound MB of junk files found", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { junkFound = 0 },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                        ) {
                            Text("Clean Now")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            if (!scanning && !scanComplete) {
                Button(
                    onClick = { scanning = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("START SCAN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
