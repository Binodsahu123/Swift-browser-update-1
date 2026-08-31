package com.swift.browser.developertoolsengine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkViewer(
    engine: InspectorEngine = InspectorEngine.instance
) {
    val requests by engine.networkRequests.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var expandedRequestId by remember { mutableStateOf<String?>(null) }

    val filteredRequests = remember(requests, searchQuery) {
        requests.filter { req ->
            req.url.contains(searchQuery, ignoreCase = true) || req.method.contains(searchQuery, ignoreCase = true)
        }.reversed()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter requests...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            IconButton(
                onClick = { engine.clearNetwork(); expandedRequestId = null },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear Requests", tint = Color.White)
            }
        }

        Divider(color = Color.DarkGray)

        if (filteredRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No network activity captured", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredRequests) { req ->
                    val isExpanded = expandedRequestId == req.id
                    NetworkRequestItem(
                        req = req,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedRequestId = if (isExpanded) null else req.id
                        }
                    )
                    Divider(color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
fun NetworkRequestItem(
    req: NetworkRequest,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val statusColor = when (req.statusCode) {
        in 200..299 -> Color(0xFF34D399)
        in 300..399 -> Color(0xFF60A5FA)
        in 400..499 -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Method Tag
            Box(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = req.method,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Status Code
            Text(
                text = "${req.statusCode}",
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Duration
            Text(
                text = "${req.durationMs}ms",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // URL
        Text(
            text = req.url,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )

        AnimatedVisibility(visible = isExpanded) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color(0xFF0F172A))
                ) {
                    Divider(color = Color(0xFF1E293B))

                    // Request Headers
                    SectionHeader("Request Headers")
                    req.requestHeaders.forEach { (k, v) ->
                        HeaderRow(key = k, value = v)
                    }

                    // Response Headers
                    SectionHeader("Response Headers")
                    req.responseHeaders.forEach { (k, v) ->
                        HeaderRow(key = k, value = v)
                    }

                    if (req.requestBody.isNotBlank()) {
                        SectionHeader("Request Payload")
                        PayloadBox(body = req.requestBody)
                    }

                    if (req.responseBody.isNotBlank()) {
                        SectionHeader("Response Body")
                        PayloadBox(body = req.responseBody)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF818CF8),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun HeaderRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "$key:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PayloadBox(body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Text(
            text = body,
            color = Color(0xFF34D399),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
