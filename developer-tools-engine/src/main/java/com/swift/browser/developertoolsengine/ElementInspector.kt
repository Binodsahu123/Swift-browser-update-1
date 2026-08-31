package com.swift.browser.developertoolsengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Style
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
fun ElementInspector(
    engine: InspectorEngine = InspectorEngine.instance
) {
    val selectedElement by engine.highlightedElementHtml.collectAsState()
    val isInspectActive by engine.inspectModeEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mode Header
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isInspectActive) Color(0xFF312E81) else Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF818CF8))
                    Column {
                        Text(
                            text = if (isInspectActive) "INSPECT ELEMENT ACTIVE" else "Element Inspector Idle",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isInspectActive) "Tap any webpage node to inspect its code" else "Enable inspect mode below",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isInspectActive,
                    onCheckedChange = { engine.setInspectModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6366F1)
                    )
                )
            }
        }

        var subTabMode by remember { mutableStateOf(0) } // 0: Visual Inspector, 1: HTML DOM Tree

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B), RoundedCornerShape(8.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { subTabMode = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTabMode == 0) Color(0xFF6366F1) else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Visual Point Inspector", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { subTabMode = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTabMode == 1) Color(0xFF6366F1) else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Full HTML Tree", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (subTabMode == 1) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                DOMViewer(engine)
            }
        } else {
            if (selectedElement.isBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No element inspected yet.\nUse the switch above and tap on the website to inspect native tags.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // HTML Node Box
                        Text("HTML Element Source", fontSize = 11.sp, color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                    text = selectedElement,
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                            )
                        }

                        // CSS Rules Mock card
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Style, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                    Text("Computed CSS & Layout Style rules", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                StyleRuleRow(property = "display", value = "block")
                                StyleRuleRow(property = "box-sizing", value = "border-box")
                                StyleRuleRow(property = "margin", value = "0px")
                                StyleRuleRow(property = "padding", value = "8px 12px")
                                StyleRuleRow(property = "font-family", value = "system-ui, sans-serif")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleRuleRow(property: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "$property:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
