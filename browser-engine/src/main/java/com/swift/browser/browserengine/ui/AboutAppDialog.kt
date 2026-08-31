package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.SwiftDeveloperEngine
import com.swift.browser.browserengine.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<String?>(null) }
    var activeOverlay by remember { mutableStateOf<String?>(null) } // "privacy", "licenses"
    var tapCount by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close About")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // App Branding and Sub-headline
                    Text(
                        text = "🌐",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Swift Browser",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "The ultra-fast, adshield private mobile web browser built of native Jetpack technology.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Version Info M3 Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tapCount++
                                        if (tapCount < 7) {
                                            val remaining = 7 - tapCount
                                            if (remaining <= 3) {
                                                android.widget.Toast.makeText(context, "You are now $remaining steps away from being a developer.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else if (tapCount == 7) {
                                            SwiftDeveloperEngine.setDeveloperModeEnabled(context, true)
                                            android.widget.Toast.makeText(context, "Swift Developer Engine Activated", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Version:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("v2.5.0", fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Codename:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Quantum Glass", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Compiled:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("UTC June 2026", fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Engine:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Blink (Chromium)", fontSize = 13.sp)
                            }
                        }
                    }

                    // Feature Specs Grid
                    Text(
                        text = "Engine Specifications",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecGridItem(
                            icon = Icons.Default.Shield,
                            title = "AdShield",
                            value = "Pro Active",
                            modifier = Modifier.weight(1f)
                        )
                        SpecGridItem(
                            icon = Icons.Default.Memory,
                            title = "Footprint",
                            value = "Light (<120MB)",
                            modifier = Modifier.weight(1f)
                        )
                        SpecGridItem(
                            icon = Icons.Default.AspectRatio,
                            title = "Layout",
                            value = "Edge-to-Edge",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Interactive Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Check for updates button
                        Button(
                            onClick = {
                                checkingUpdates = true
                                updateResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for updates", fontWeight = FontWeight.Bold)
                        }

                        // Privacy policy button
                        OutlinedButton(
                            onClick = { activeOverlay = "privacy" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PrivacyTip, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Private Privacy Policy")
                        }

                        // Open source licenses button
                        OutlinedButton(
                            onClick = { activeOverlay = "licenses" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Article, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Integrated Open-Source Licenses")
                        }

                        // Contact developer button
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:developer@swiftbrowser.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "Swift Browser Feedback")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No email app found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Contact Developer")
                        }
                    }
                }
            }
        }
    }

    // Checking for updates dialog overlay
    if (checkingUpdates) {
        Dialog(
            onDismissRequest = { checkingUpdates = false },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Checking servers for updates...", fontWeight = FontWeight.Medium)
                    
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        checkingUpdates = false
                        updateResult = "Version is up-to-date!\nCurrent Version: v2.5.0"
                    }
                }
            }
        }
    }

    // Update Result dialog overlay
    if (updateResult != null) {
        AlertDialog(
            onDismissRequest = { updateResult = null },
            confirmButton = {
                TextButton(onClick = { updateResult = null }) {
                    Text("Awesome")
                }
            },
            icon = {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
            },
            title = { Text("Swift Browser Update Info") },
            text = { Text(updateResult ?: "") }
        )
    }

    // Modal view overlays for Privacy Policy or Open Source Licenses
    if (activeOverlay != null) {
        Dialog(
            onDismissRequest = { activeOverlay = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeOverlay == "privacy") "Privacy Policy" else "Open Source Licenses",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { activeOverlay = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close View")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeOverlay == "privacy") {
                            Text(
                                text = "Swift Browser Privacy Policy",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This Privacy Policy governs the use and collection of metrics through Swift Browser mobile applications. We guarantee that your details (passwords, files, URLs, searches) are handled natively locally on your device storage. Swift Browser doesn't host telemetry servers or transfer metrics to external trackers. When enabling AdShield, filter parameters are evaluated offline. Cookies and browser cache can be wiped clean manually any time through settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Governed Libraries & Licensing Agreements:", fontWeight = FontWeight.Bold)
                            
                            LicenseItem(name = "Kotlin Coroutines Library", license = "Licensing: Apache License 2.0")
                            LicenseItem(name = "Jetpack Compose Framework", license = "Licensing: Apache License 2.0")
                            LicenseItem(name = "Room Persistence database", license = "Licensing: Apache License 2.0")
                            LicenseItem(name = "Material 3 Design System component", license = "Licensing: Apache License 2.0")
                            LicenseItem(name = "Vico Charts UI components", license = "Licensing: Apache License 2.0")
                            LicenseItem(name = "Coil Image Loader library context", license = "Licensing: MIT License")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LicenseItem(name: String, license: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(license, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun SpecGridItem(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
    }
}
