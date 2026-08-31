package com.swift.browser.translateengine.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.translateengine.ProgressState
import com.swift.browser.translateengine.TranslateEngineApi
import com.swift.browser.translateengine.TranslationDebugger
import com.swift.browser.translateengine.TranslationProgressManager

@Composable
fun TranslateEngineUi(
    engine: TranslateEngineApi,
    isDesktopMode: Boolean,
    activeWebView: android.webkit.WebView?,
    tabId: String?,
    currentUrl: String?,
    modifier: Modifier = Modifier
) {
    val uiState by engine.uiState.collectAsState()

    TranslateBarLayout(
        isVisible = uiState.isVisible,
        isDesktopMode = isDesktopMode,
        isPageTranslated = uiState.isPageTranslated,
        detectedLang = uiState.detectedLanguage,
        targetLang = uiState.targetLanguageName,
        targetLangCode = uiState.targetLanguageCode,
        currentHost = uiState.currentHost.ifEmpty {
            try { android.net.Uri.parse(currentUrl ?: "").host ?: "" } catch (_: Exception) { "" }
        },
        progressManager = engine.progressManager,
        debugger = engine.debugger,
        onTranslate = { targetLangCode ->
            engine.translateActivePage(
                targetLangCode = targetLangCode.ifEmpty { uiState.targetLanguageCode },
                webView = activeWebView,
                tabId = tabId,
                currentUrl = currentUrl,
                isDesktop = isDesktopMode
            )
        },
        onUndo = {
            engine.undoTranslation(activeWebView, tabId, currentUrl)
        },
        onDismiss = {
            engine.dismissTranslateBar(activeWebView, tabId, currentUrl)
        },
        onNeverTranslateSite = { host ->
            engine.settings.addNeverTranslateSite(host)
            engine.dismissTranslateBar(activeWebView, tabId, currentUrl)
        },
        onNeverTranslateLanguage = { lang ->
            engine.settings.addNeverTranslateLanguage(lang)
            engine.dismissTranslateBar(activeWebView, tabId, currentUrl)
        },
        modifier = modifier
    )
}

@Composable
fun TranslateBarLayout(
    isVisible: Boolean,
    isDesktopMode: Boolean,
    isPageTranslated: Boolean,
    detectedLang: String,
    targetLang: String,
    targetLangCode: String,
    currentHost: String,
    progressManager: TranslationProgressManager,
    debugger: TranslationDebugger,
    onTranslate: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onNeverTranslateSite: (String) -> Unit,
    onNeverTranslateLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTranslationDiagnostics by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isDesktopMode) {
                DesktopTranslationBar(
                    isPageTranslated = isPageTranslated,
                    detectedLang = detectedLang,
                    targetLang = targetLang,
                    targetLangCode = targetLangCode,
                    currentHost = currentHost,
                    progressManager = progressManager,
                    onTranslate = onTranslate,
                    onUndo = onUndo,
                    onDismiss = onDismiss,
                    onNeverTranslateSite = onNeverTranslateSite,
                    onNeverTranslateLanguage = onNeverTranslateLanguage,
                    onShowDiagnostics = { showTranslationDiagnostics = true }
                )
            } else {
                MobileTranslationBar(
                    isPageTranslated = isPageTranslated,
                    detectedLang = detectedLang,
                    targetLang = targetLang,
                    targetLangCode = targetLangCode,
                    currentHost = currentHost,
                    progressManager = progressManager,
                    onTranslate = onTranslate,
                    onUndo = onUndo,
                    onDismiss = onDismiss,
                    onNeverTranslateSite = onNeverTranslateSite,
                    onNeverTranslateLanguage = onNeverTranslateLanguage,
                    onShowDiagnostics = { showTranslationDiagnostics = true }
                )
            }
        }
    }

    if (showTranslationDiagnostics) {
        TranslationDiagnosticsDialog(
            debugger = debugger,
            onDismiss = { showTranslationDiagnostics = false }
        )
    }
}

@Composable
fun MobileTranslationBar(
    isPageTranslated: Boolean,
    detectedLang: String,
    targetLang: String,
    targetLangCode: String,
    currentHost: String,
    progressManager: TranslationProgressManager,
    onTranslate: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onNeverTranslateSite: (String) -> Unit,
    onNeverTranslateLanguage: (String) -> Unit,
    onShowDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMoreLanguages by remember { mutableStateOf(false) }
    val effectiveDetectedLang = detectedLang.ifEmpty { "en" }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .wrapContentHeight()
            .testTag("mobile_translation_bar"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translate",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    AssistChip(
                        onClick = { showMoreLanguages = true },
                        label = {
                            Text(
                                text = targetLang,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.testTag("mobile_language_selector")
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(32.dp).testTag("mobile_translate_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Options",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Choose language...", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    showMoreLanguages = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this page", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    if (currentHost.isNotEmpty()) {
                                        onNeverTranslateSite(currentHost)
                                        Toast.makeText(context, "Never translate $currentHost added", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this language (${effectiveDetectedLang.uppercase()})", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onNeverTranslateLanguage(effectiveDetectedLang)
                                    Toast.makeText(context, "Never translate ${effectiveDetectedLang.uppercase()} pages", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Translation Diagnostics", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onShowDiagnostics()
                                }
                            )
                        }
                    }

                    if (isPageTranslated) {
                        TextButton(
                            onClick = onUndo,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(32.dp).testTag("mobile_translate_undo")
                        ) {
                            Text("Show Original", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { onTranslate(targetLangCode) },
                            modifier = Modifier.height(32.dp).testTag("mobile_translate_do")
                        ) {
                            Text("Translate", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("mobile_translate_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TranslationProgressSection(
                progressManager = progressManager,
                onRetry = { onTranslate(targetLangCode) }
            )
        }
    }

    if (showMoreLanguages) {
        TranslateLanguageDialog(
            onTranslate = { langCode ->
                showMoreLanguages = false
                onTranslate(langCode)
            },
            onDismiss = { showMoreLanguages = false }
        )
    }
}

@Composable
fun DesktopTranslationBar(
    isPageTranslated: Boolean,
    detectedLang: String,
    targetLang: String,
    targetLangCode: String,
    currentHost: String,
    progressManager: TranslationProgressManager,
    onTranslate: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onNeverTranslateSite: (String) -> Unit,
    onNeverTranslateLanguage: (String) -> Unit,
    onShowDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMoreLanguages by remember { mutableStateOf(false) }
    val effectiveDetectedLang = detectedLang.ifEmpty { "en" }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .wrapContentHeight()
            .testTag("desktop_translation_bar"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translate Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isPageTranslated) "Translated" else "Translate page",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = if (isPageTranslated) "to $targetLang" else "from ${effectiveDetectedLang.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    AssistChip(
                        onClick = { showMoreLanguages = true },
                        label = {
                            Text(
                                text = targetLang,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.testTag("desktop_target_language_chip")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(32.dp).testTag("desktop_translate_settings")
                        ) {
                            Icon(Icons.Default.Settings, "Translation Options", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this page", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    if (currentHost.isNotEmpty()) {
                                        onNeverTranslateSite(currentHost)
                                        Toast.makeText(context, "Never translate $currentHost added", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this language (${effectiveDetectedLang.uppercase()})", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onNeverTranslateLanguage(effectiveDetectedLang)
                                    Toast.makeText(context, "Never translate ${effectiveDetectedLang.uppercase()} pages", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Translation Diagnostics", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onShowDiagnostics()
                                }
                            )
                        }
                    }

                    if (!isPageTranslated) {
                        Button(
                            onClick = { onTranslate(targetLangCode) },
                            modifier = Modifier.height(32.dp).testTag("desktop_translate_button")
                        ) {
                            Text("Translate", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onUndo,
                            modifier = Modifier.height(32.dp).testTag("desktop_show_original_button")
                        ) {
                            Text("Show Original", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("desktop_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Toolbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            TranslationProgressSection(
                progressManager = progressManager,
                onRetry = { onTranslate(targetLangCode) }
            )
        }
    }

    if (showMoreLanguages) {
        TranslateLanguageDialog(
            onTranslate = { langCode ->
                showMoreLanguages = false
                onTranslate(langCode)
            },
            onDismiss = { showMoreLanguages = false }
        )
    }
}

@Composable
fun TranslationDiagnosticsDialog(
    debugger: TranslationDebugger,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Translation Debug Panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Real-time Native Browser Translation Telemetry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val totalNodes = debugger.textNodesFound.get()
                val translatedNodes = debugger.textNodesTranslated.get()
                val failedNodes = maxOf(0, totalNodes - translatedNodes)
                val originalNodes = totalNodes
                val successRate = if (totalNodes > 0) {
                    (translatedNodes.toDouble() / totalNodes.toDouble()) * 100.0
                } else {
                    100.0
                }
                val successRateStr = String.format(java.util.Locale.US, "%.1f%%", successRate)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Detected Language:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(debugger.detectedLanguage.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Language:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(debugger.targetLanguage.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$totalNodes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Translated Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$translatedNodes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Failed Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$failedNodes", fontWeight = FontWeight.Bold, color = if (failedNodes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Original Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$originalNodes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Replacement Success Rate:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(successRateStr, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cache Hits (Memory/Room):", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("${debugger.cacheHits.get()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Translation Latency:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("${debugger.totalTranslationTimeMs.get()} ms", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
fun TranslationProgressSection(
    progressManager: TranslationProgressManager,
    onRetry: () -> Unit
) {
    val state by progressManager.state.collectAsState()
    val total by progressManager.totalNodes.collectAsState()
    val translated by progressManager.translatedNodes.collectAsState()

    if (state == ProgressState.Idle) return

    val infiniteTransition = rememberInfiniteTransition(label = "translation_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translation_rotation"
    )

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("translation_progress_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (state) {
                    ProgressState.Translating -> {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Translating",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (total > 0) "Translating $translated / $total" else "Translating...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    ProgressState.Completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✓ Translation Complete",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    ProgressState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠ Translation Failed",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }

            if (state == ProgressState.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.height(32.dp).testTag("translation_progress_retry")
                ) {
                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
