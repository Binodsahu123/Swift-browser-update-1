package com.swift.browser.downloaduiengine

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    var fileContent by remember { mutableStateOf("Loading file content...") }
    var fontSize by remember { mutableFloatStateOf(12f) }
    var showFontSizeSlider by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                fileContent = file.readText(Charsets.UTF_8)
            } else {
                fileContent = "Error: File does not exist."
            }
        } catch (e: Exception) {
            fileContent = "Error reading text content: ${e.localizedMessage}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showFontSizeSlider = !showFontSizeSlider }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Font Size", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F14))
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showFontSizeSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161622))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Size: ${fontSize.toInt()}sp", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 8f..32f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF2E2E), activeTrackColor = Color(0xFFFF2E2E)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = fileContent,
                    color = Color.LightGray,
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
