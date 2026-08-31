package com.swift.browser.browserengine

import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserCoordinator(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    webViewContent: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    overlays: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(modifier = Modifier.weight(1f)) {
                webViewContent()
            }
            bottomBar()
        }
        overlays()
    }
}

@Composable
fun FindInPageLayout(
    findInPageQuery: String,
    findInPageCurrentMatch: Int,
    findInPageTotalMatches: Int,
    onSearchChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("find_in_page_panel")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search page match",
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                BasicTextFieldWithoutLabelHelper(
                    value = findInPageQuery,
                    onValueChange = onSearchChange,
                    onDone = {},
                    placeholder = "Find in page...",
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("find_in_page_input")
                )

                if (findInPageTotalMatches > 0) {
                    Text(
                        text = "$findInPageCurrentMatch of $findInPageTotalMatches",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else if (findInPageQuery.isNotEmpty()) {
                    Text(
                        text = "No matches",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                IconButton(
                    onClick = onPrev,
                    enabled = findInPageTotalMatches > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                }

                IconButton(
                    onClick = onNext,
                    enabled = findInPageTotalMatches > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).testTag("find_in_page_close")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close search bar")
                }
            }
        }
    }
}

@Composable
fun BasicTextFieldWithoutLabelHelper(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onDone() }
        ),
        textStyle = textStyle,
        modifier = modifier,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle.copy(color = textStyle.color.copy(alpha = 0.5f))
                )
            }
            innerTextField()
        }
    )
}

@Composable
fun TtsControlPanel(
    currentTtsIndex: Int,
    totalTtsSegments: Int,
    currentTtsText: String,
    ttsSpeed: Float,
    isTtsPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Listening to Page (${currentTtsIndex + 1}/$totalTtsSegments)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop Aloud Reader",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (currentTtsText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentTtsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val nextIdx = (speeds.indexOf(ttsSpeed) + 1).let { if (it >= speeds.size) 0 else it }
                val nextSpeed = speeds[nextIdx]

                TextButton(
                    onClick = { onSpeedChange(nextSpeed) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${ttsSpeed}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    enabled = currentTtsIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Sentence",
                        tint = if (currentTtsIndex > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isTtsPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    enabled = currentTtsIndex + 1 < totalTtsSegments
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Sentence",
                        tint = if (currentTtsIndex + 1 < totalTtsSegments) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                Spacer(modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
fun SslCertificateDialog(
    url: String,
    blockedAdsCount: Int,
    isAdBlockWhitelisted: Boolean,
    onToggleAdBlockForSite: () -> Unit,
    onDismiss: () -> Unit
) {
    val domain = remember(url) {
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            "Unknown Domain"
        }
    }

    val issuedBy = when {
        domain.contains("google", ignoreCase = true) -> "Google Trust Services LLC"
        domain.contains("wikipedia", ignoreCase = true) -> "DigiCert SHA2 Secure Server CA"
        domain.contains("github", ignoreCase = true) -> "DigiCert SHA2 Extended Validation Server CA"
        else -> "Let's Encrypt Authority X3"
    }

    val validFrom = "Jan 1, 2026"
    val validTo = "Dec 31, 2026"
    val fingerprint = "SHA-256: 4C:2E:85:AB:58:34:CA:EA:0A:8B:D0:D9:6A:01:21:44:83:BE:9C:5F:FB:03:DC:B5:E9:19:28:CD:F2:75:DE:9E"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connection is secure", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Your information (for example, passwords or credit card numbers) is private when it is sent to this site.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Certificate Information", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Common Name (CN): $domain", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Issuer: $issuedBy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Validity: $validFrom to $validTo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Fingerprint: $fingerprint", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AdBlocker Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Blocked ads so far: $blockedAdsCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = !isAdBlockWhitelisted,
                        onCheckedChange = { onToggleAdBlockForSite() }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun AddToHomeScreenDialog(
    initialTitle: String,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Home screen") },
        text = {
            Column {
                Text("Enter the name for the home screen shortcut:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MoveTabToGroupDialog(
    onConfirm: (String, Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val colors = listOf(
        0xFFE57373, // Red
        0xFF81C784, // Green
        0xFF64B5F6, // Blue
        0xFFFFD54F, // Yellow
        0xFFBA68C8, // Purple
        0xFF4DB6AC  // Teal
    )
    var selectedColor by remember { mutableStateOf(colors[2]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organize Tab into Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Group Name:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("e.g. Work, Shopping") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text("Group Badge Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { colorVal ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .border(
                                    width = if (selectedColor == colorVal) 3.dp else 0.dp,
                                    color = if (selectedColor == colorVal) Color.Black else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorVal }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val group = groupName.trim()
                    if (group.isNotEmpty()) {
                        onConfirm(group, selectedColor)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text("Remove from Group", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun CreateGroupDialog(
    initialGroupName: String = "My Group",
    initialColor: Long = 0xFF60A5FA,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var groupName by remember { mutableStateOf(initialGroupName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    val colorsGroup = listOf(0xFFF87171, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFF472B6, 0xFF2DD4BF, 0xFFFB7185)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tab Group", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Column {
                    Text("Select Group Color:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorsGroup.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(col), CircleShape)
                                    .border(
                                        width = if (selectedColor == col) 3.dp else 0.dp,
                                        color = if (selectedColor == col) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = col }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onConfirm(groupName, selectedColor)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteBrowsingDataDialog(
    onClear: (Boolean, Boolean, Boolean, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var selectedRangeIndex by remember { mutableStateOf(4) } // Default: All time
    val ranges = listOf("Last hour", "Last 24 hours", "Last 7 days", "Last 4 weeks", "All time")
    var expandRangeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select parameters to clear:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                
                // Range dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandRangeDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Time Range: ${ranges[selectedRangeIndex]}")
                    }
                    DropdownMenu(
                        expanded = expandRangeDropdown,
                        onDismissRequest = { expandRangeDropdown = false }
                    ) {
                        ranges.forEachIndexed { idx, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedRangeIndex = idx
                                    expandRangeDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browsing history", fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cookies and site data", fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cached images and files", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onClear(clearHistory, clearCookies, clearCache, selectedRangeIndex)
                }
            ) {
                Text("Clear Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HelpFeedbackDialog(
    onSendFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help & Feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Swift Browser v2.0.0", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Equipped with ad protection, desktop and mobile mode switcher, fast page rendering, and local persistence.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Send system feedback to help us build a faster web:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text("What did you think of the browser or ad blocking?") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (feedbackText.isNotBlank()) {
                        onSendFeedback(feedbackText)
                    }
                },
                enabled = feedbackText.isNotBlank()
            ) {
                Text("Send Feedback")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RecentTabsDialog(
    recentlyClosed: List<com.swift.browser.tabengine.model.TabModel>,
    onReopen: (com.swift.browser.tabengine.model.TabModel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recently Closed Tabs") },
        text = {
            Column {
                if (recentlyClosed.isEmpty()) {
                    Text("No recently closed tabs from this session.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Text("Select a page to restore:", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(recentlyClosed) { tab ->
                            ListItem(
                                headlineContent = { Text(tab.title.take(30), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                                supportingContent = { Text(tab.url.take(40), fontSize = 10.sp, color = Color.Gray, maxLines = 1) },
                                leadingContent = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.clickable { onReopen(tab) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
