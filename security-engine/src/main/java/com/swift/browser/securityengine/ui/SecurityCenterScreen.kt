package com.swift.browser.securityengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.securityengine.SwiftSecurityEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shieldState by SwiftSecurityEngine.shieldState.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("swift_security_center_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Security Shield Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Secure Origin Boundaries", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }

                // Block Tracker Card
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automated Tracker Blocker", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Stops analytic metrics and hidden marketing telemetry links from loading.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = shieldState.trackerBlockingEnabled,
                                onCheckedChange = { SwiftSecurityEngine.updateShieldState(trackerBlocking = it) }
                            )
                        }
                    }
                }

                // HTTPS Upgrade Card
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Force HTTPS Upgrades", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Redirect cleartext HTTP connections to secure TLS ports automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = shieldState.httpsUpgradeEnabled,
                                onCheckedChange = { SwiftSecurityEngine.updateShieldState(httpsUpgrade = it) }
                            )
                        }
                    }
                }

                // Cookie Isolation Card
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Per-Site Storage Isolation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Prevents sites from reading cookies set by other origins.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = shieldState.cookieIsolationEnabled,
                                onCheckedChange = { SwiftSecurityEngine.updateShieldState(cookieIsolation = it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
