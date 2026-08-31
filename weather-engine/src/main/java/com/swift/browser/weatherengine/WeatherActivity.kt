package com.swift.browser.weatherengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var temperature by remember { mutableStateOf("--") }
    var condition by remember { mutableStateOf("Fetching Location...") }
    var location by remember { mutableStateOf("Locating...") }

    LaunchedEffect(Unit) {
        delay(1500) // Simulate network/location fetch
        location = "Jabalpur, MP"
        temperature = "28°C"
        condition = "Clear Sky. No rain expected."
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Engine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, "Advanced Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E293B))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Icon(Icons.Default.LocationOn, "Location", tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(location, fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            } else {
                Icon(Icons.Default.WbSunny, "Weather", tint = Color(0xFFF59E0B), modifier = Modifier.size(120.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(temperature, fontSize = 64.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(condition, fontSize = 18.sp, color = Color.LightGray)
            }
            Spacer(modifier = Modifier.weight(1f))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Advanced Details", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Humidity: 65%", color = Color.LightGray)
                    Text("Wind: 12 km/h", color = Color.LightGray)
                    Text("Precipitation Chance: 0%", color = Color.LightGray)
                }
            }
        }
    }
}
