package com.swift.browser.extensionengine.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionEngineApi

@Composable
fun ScriptRunnerSubPanel(
    api: ExtensionEngineApi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scriptText by remember { mutableStateOf("// Execute JavaScript snippet in active tab context\nconsole.log('Script runner active');") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Extension Script Sandbox",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = scriptText,
            onValueChange = { scriptText = it },
            placeholder = { Text("Enter JavaScript code...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    api.setCustomExtensionScript(scriptText, true)
                    Toast.makeText(context, "Snippet set as active user script", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run Snippet")
            }
        }
    }
}
