package com.swift.browser.developertoolsengine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DOMViewer(
    engine: InspectorEngine = InspectorEngine.instance
) {
    val liveHtml by engine.fullDOM.collectAsState()
    val rawHtml = liveHtml.ifBlank { "<!-- Fetching page DOM tree... -->\n<html>\n  <head>\n    <title>Swift Browser</title>\n  </head>\n  <body>\n    <div id=\"app\">\n      <h1>Welcome to Swift Browser!</h1>\n      <p>Developer inspect panel active. Inspect elements natively.</p>\n    </div>\n  </body>\n</html>" }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Break down lines of raw html
                val lines = rawHtml.split("\n")
                items(lines.zip(lines.indices)) { (line, idx) ->
                    HtmlLineRow(line, idx)
                }
            }
        }
    }
}

@Composable
fun HtmlLineRow(line: String, index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%3d ", index + 1),
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )

        val trimmed = line.trim()
        val indentCount = line.length - trimmed.length
        val indentSpace = " ".repeat(indentCount)

        val syntaxColoredText = remember(trimmed) {
            getColoredHtml(trimmed)
        }

        Text(
            text = "$indentSpace$syntaxColoredText",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )
    }
}

private fun getColoredHtml(trimmed: String): String {
    // Basic formatting for html tags without requiring full parser libraries
    return trimmed
        .replace("<", " < ")
        .replace(">", " > ")
}
