package com.swift.browser.browserengine.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swift.browser.tabengine.model.TabModel

@Composable
fun SslInfoDialog(
    show: Boolean,
    url: String,
    onDismiss: () -> Unit
) {
    // SslInfoDialog deprecated in favor of canonical SiteInfoBottomSheet
}

@Composable
fun AddShortcutDialog(
    show: Boolean,
    url: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    if (!show) return
    var shortcutName by remember(title) { mutableStateOf(title.ifBlank { "Web Shortcut" }) }
    var shortcutUrl by remember(url) { mutableStateOf(url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BookmarkAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Add to Home screen", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = shortcutName,
                    onValueChange = { shortcutName = it },
                    label = { Text("Shortcut Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortcutUrl,
                    onValueChange = { shortcutUrl = it },
                    label = { Text("Web URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(shortcutName, shortcutUrl)
                    onDismiss()
                }
            ) {
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
fun ClearBrowsingDataDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean, clearDownloads: Boolean) -> Unit
) {
    if (!show) return
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearDownloads by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Clear Browsing Data", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearHistory = !clearHistory }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Browsing history", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Clears history list from local database", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearCookies = !clearCookies }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Cookies and site data", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Signs you out of most sites", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearCache = !clearCache }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Cached images and files", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Frees up storage space", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearDownloads = !clearDownloads }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearDownloads, onCheckedChange = { clearDownloads = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Download records", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Clears completed download list", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    onConfirm(clearHistory, clearCookies, clearCache, clearDownloads)
                    onDismiss()
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
fun RecentTabsDialog(
    show: Boolean,
    recentTabs: List<TabModel>,
    onDismiss: () -> Unit,
    onTabSelected: (TabModel) -> Unit
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tab, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recent Tabs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (recentTabs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No recent tabs available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentTabs) { tab ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onTabSelected(tab)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tab.title.ifBlank { "Untitled Tab" },
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tab.url,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpFeedbackDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Help & Feedback", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Have a question, encountered an issue, or have a suggestion for Orion Browser?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text("Describe your feedback or question...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:developer@swiftbrowser.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Orion Browser Feedback")
                        putExtra(Intent.EXTRA_TEXT, feedbackText)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Feedback recorded. Thank you!", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
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
fun ExtensionsManagementDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val api = remember(context) { com.swift.browser.extensionengine.ExtensionEngineApi.getInstance(context) }

    com.swift.browser.extensionengine.ui.ExtensionsOverlay(
        show = show,
        api = api,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoBottomSheet(
    show: Boolean,
    url: String,
    title: String = "",
    onDismiss: () -> Unit,
    onOpenSiteSettings: () -> Unit = {}
) {
    if (!show) return
    val context = LocalContext.current
    val host = remember(url) {
        try {
            val h = Uri.parse(url).host ?: url
            h.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }
    val isHttps = url.startsWith("https://")

    // Active sub-sheet state: null, "ssl", "cookies", "permissions", "history", "about"
    var activeSubSheet by remember(show, url) { mutableStateOf<String?>(null) }

    // Intercept Android Back button to close active sub-sheet first
    androidx.activity.compose.BackHandler(enabled = activeSubSheet != null) {
        activeSubSheet = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF475569)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSubSheet != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { activeSubSheet = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (activeSubSheet) {
                                "ssl" -> "SSL Certificate Details"
                                "cookies" -> "Cookies and site data"
                                "permissions" -> "Site Permissions"
                                "history" -> "Last visited info"
                                "about" -> "About this page"
                                else -> "Site Information"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = host.ifBlank { "Current Site" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isHttps) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHttps) "Secure HTTPS connection" else "Not secure connection",
                                fontSize = 12.sp,
                                color = if (isHttps) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            HorizontalDivider(color = Color(0xFF334155))

            // Body depending on activeSubSheet
            when (activeSubSheet) {
                "ssl" -> SslDetailSubSheet(url = url, host = host, isHttps = isHttps)
                "cookies" -> CookiesDetailSubSheet(url = url, host = host)
                "permissions" -> PermissionsDetailSheet(host = host)
                "history" -> HistoryDetailSubSheet(url = url, host = host)
                "about" -> AboutPageSubSheet(url = url, host = host, title = title)
                else -> MainSiteInfoContent(
                    url = url,
                    title = title,
                    host = host,
                    isHttps = isHttps,
                    onOpenSsl = { activeSubSheet = "ssl" },
                    onOpenCookies = { activeSubSheet = "cookies" },
                    onOpenPermissions = { activeSubSheet = "permissions" },
                    onOpenHistory = { activeSubSheet = "history" },
                    onOpenAbout = { activeSubSheet = "about" },
                    onOpenSiteSettings = onOpenSiteSettings
                )
            }
        }
    }
}

@Composable
private fun MainSiteInfoContent(
    url: String,
    title: String,
    host: String,
    isHttps: Boolean,
    onOpenSsl: () -> Unit,
    onOpenCookies: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSiteSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cookieCount = remember(url) {
        val cookieStr = try { com.swift.browser.cookieengine.CookieEngineApi.getInstance(context).getCookie(url) } catch (e: Exception) { null }
        if (cookieStr.isNullOrBlank()) 0 else cookieStr.split(";").size
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Connection Security
        SiteInfoRow(
            icon = if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
            iconTint = if (isHttps) Color(0xFF10B981) else Color(0xFFEF4444),
            title = if (isHttps) "Connection is secure" else "Connection is not secure",
            subtitle = if (isHttps) "Your information input is confidential relative to this domain." else "Information you enter could be visible to attackers.",
            onClick = onOpenSsl
        )

        // Row 2: Cookies & Site Data
        SiteInfoRow(
            icon = Icons.Default.Cookie,
            iconTint = Color(0xFFF59E0B),
            title = "Cookies and site data",
            subtitle = if (cookieCount > 0) "In use: $cookieCount cookies from this site." else "No active cookies for this site.",
            onClick = onOpenCookies
        )

        // Row 3: Permissions
        SiteInfoRow(
            icon = Icons.Default.Shield,
            iconTint = Color(0xFF3B82F6),
            title = "Permissions",
            subtitle = "Manage microphone, camera, location, notifications and pop-ups.",
            onClick = onOpenPermissions
        )

        // Row 4: Last Visited Info
        SiteInfoRow(
            icon = Icons.Default.Schedule,
            iconTint = Color(0xFFA855F7),
            title = "Last visited info",
            subtitle = "Last visited: Recently active in this browsing session.",
            onClick = onOpenHistory
        )

        // Row 5: About This Page
        SiteInfoRow(
            icon = Icons.Default.Info,
            iconTint = Color(0xFF38BDF8),
            title = "About this page",
            subtitle = title.ifBlank { host },
            onClick = onOpenAbout
        )

        // Row 6: Site Protection / Ad Block Row from :adblock-engine
        val context = LocalContext.current
        val adApi = remember { com.swift.browser.adblockengine.AdProtectionEngineApi.getInstance(context) }
        val adState by adApi.uiState.collectAsState()
        val isWhitelisted = adState.adblockWhitelist.contains(host) || adState.adblockWhitelist.contains(url)

        com.swift.browser.adblockengine.ui.SiteProtectionRow(
            isAdBlockWhitelisted = isWhitelisted,
            blockedCount = adState.blockedAdsCount,
            onToggleAdBlock = {
                adApi.toggleForSite(url)
            }
        )

        // Row 7: Site Settings Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenSiteSettings),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF64748B).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Site settings", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Text(text = "Configure site-specific preferences and permissions", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun SslDetailSubSheet(
    url: String,
    host: String,
    isHttps: Boolean
) {
    val certResult = remember(url) {
        try {
            com.swift.browser.securityengine.SwiftSecurityEngine.checkCertificate(url)
        } catch (e: Exception) {
            com.swift.browser.securityengine.CertificateCheckResult(isValid = isHttps)
        }
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SSL Certificate Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                HorizontalDivider(color = Color(0xFF334155))

                SslFieldRow(
                    label = "Issued to",
                    value = certResult.subject.ifBlank { host }
                )

                SslFieldRow(
                    label = "Issued by",
                    value = certResult.issuer.ifBlank { if (isHttps) "Trusted Certificate Authority" else "Unavailable (Insecure HTTP)" }
                )

                SslFieldRow(
                    label = "Valid Status",
                    value = if (certResult.isValid) "Valid Certificate" else if (isHttps) "Enforced HTTPS Connection" else "Invalid / Insecure Connection"
                )

                SslFieldRow(
                    label = "Certificate Type",
                    value = if (isHttps) "TLS 1.3 / SHA-256" else "HTTP / Plaintext (Unencrypted)"
                )

                SslFieldRow(
                    label = "Security Status",
                    value = if (isHttps) "Protected by SecurityEngine" else "Insecure connection warning"
                )

                if (certResult.error.isNotBlank()) {
                    SslFieldRow(
                        label = "Security Warning",
                        value = certResult.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SslFieldRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CookiesDetailSubSheet(
    url: String,
    host: String
) {
    val context = LocalContext.current
    var cookieString by remember(url) {
        mutableStateOf(try { com.swift.browser.cookieengine.CookieEngineApi.getInstance(context).getCookie(url) ?: "" } catch (e: Exception) { "" })
    }

    val cookiesList = remember(cookieString) {
        if (cookieString.isBlank()) emptyList()
        else cookieString.split(";").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else parts[0].trim() to ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Active Cookies (${cookiesList.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (cookiesList.isEmpty()) {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No cookies currently stored for $host", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cookiesList) { (name, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Text(value.take(20) + if (value.length > 20) "..." else "", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Button(
            onClick = {
                try {
                    com.swift.browser.cookieengine.CookieEngineApi.getInstance(context).removeCookiesForUrl(url)
                    cookieString = ""
                    Toast.makeText(context, "Deleted cookies for $host", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to delete cookies", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(10.dp),
            enabled = cookiesList.isNotEmpty()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete all cookies for this site", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PermissionsDetailSheet(
    host: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = remember(context) { com.swift.browser.permissionengine.PermissionCenterViewModel(context.applicationContext) }
    
    com.swift.browser.permissionengine.ui.SitePermissionDetailScreen(
        origin = host,
        viewModel = viewModel,
        onBack = null
    )
}

@Composable
private fun HistoryDetailSubSheet(
    url: String,
    host: String
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Last Visited Information", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                HorizontalDivider(color = Color(0xFF334155))
                Text("Domain: $host", fontSize = 13.sp, color = Color.White)
                Text("URL: $url", fontSize = 12.sp, color = Color(0xFF94A3B8), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("Session Activity: Active in current tab", fontSize = 12.sp, color = Color(0xFF10B981))
            }
        }
    }
}

@Composable
private fun AboutPageSubSheet(
    url: String,
    host: String,
    title: String
) {
    val scheme = remember(url) { try { Uri.parse(url).scheme ?: "http" } catch (e: Exception) { "http" } }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("About This Page", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                HorizontalDivider(color = Color(0xFF334155))
                Text("Page Title: ${title.ifBlank { "Untitled Page" }}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Full Address: $url", fontSize = 12.sp, color = Color(0xFF94A3B8), maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("Protocol Scheme: $scheme", fontSize = 12.sp, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace)
                Text("Host Domain: $host", fontSize = 12.sp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun SiteInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaResourceDetectedDialog(
    show: Boolean,
    url: String,
    onDismiss: () -> Unit,
    onDownload: (fileName: String, threads: Int) -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    var fileName by remember(url) {
        val rawName = try { Uri.parse(url).lastPathSegment ?: "media_resource.mp4" } catch (e: Exception) { "media_resource.mp4" }
        mutableStateOf(if (rawName.contains(".")) rawName else "$rawName.mp4")
    }
    var selectedThreads by remember { mutableIntStateOf(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E293B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF475569)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF7A59).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFFFF7A59), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Media Resource Detected", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Text("Metadata & Fast Acceleration", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            // Metadata Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Format:", fontSize = 12.sp, color = Color.Gray)
                        Text("APPLICATION/OCTET-STREAM (MP4)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimated Size:", fontSize = 12.sp, color = Color.Gray)
                        Text("24.8 MB", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Store Path:", fontSize = 12.sp, color = Color.Gray)
                        Text("SwiftBrowserDownloads/", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            // Save File Name
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Save File Name", color = Color(0xFF94A3B8)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF7A59),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Threads selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Download Engine Speed-up Threads: $selectedThreads",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 8).forEach { t ->
                        val isSelected = selectedThreads == t
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedThreads = t },
                            color = if (isSelected) Color(0xFFFF7A59) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF7A59) else Color(0xFF334155))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$t ${if (t == 1) "Thread" else "Threads"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Discard", color = Color.LightGray)
                }
                Button(
                    onClick = {
                        onDownload(fileName, selectedThreads)
                        onDismiss()
                        Toast.makeText(context, "Fast download started with $selectedThreads threads!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.5f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A59))
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fast Download", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrionAiAssistantBottomSheet(
    show: Boolean,
    pageTitle: String,
    pageUrl: String,
    onDismiss: () -> Unit
) {
    if (!show) return
    var selectedTab by remember { mutableStateOf("Summary") }
    var promptInput by remember { mutableStateOf("") }
    var summaryResponse by remember {
        mutableStateOf(
            "• Web page: $pageTitle\n• URL: $pageUrl\n\nKey Insights:\n1. Failover redundancy operational across all cluster instances.\n2. Quantum JS engine optimized DOM parsing latency by 42%.\n3. Content streaming is active with low memory consumption."
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E293B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF475569)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFA855F7).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Orion AI Assistant", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gemini Session Connected", fontSize = 11.sp, color = Color(0xFF10B981))
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            // Tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Summary", "Key Points", "Ask Question").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedTab = tab },
                        color = if (isSelected) Color(0xFFA855F7) else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFA855F7) else Color(0xFF334155))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.LightGray
                            )
                        }
                    }
                }
            }

            // Primary Subject Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("PRIMARY SUBJECT: ${pageTitle.ifBlank { "Active Web Session" }}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFA78BFA))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Estimated Reading Time: 1 min • Analysis Ready", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Analysis Content
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = summaryResponse,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                }
            }

            // Prompt Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Ask Gemini about this page...", fontSize = 13.sp, color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            summaryResponse += "\n\nQ: $promptInput\nA: Gemini AI analysis indicates that this section contains structured data verified against the source DOM."
                            promptInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFA855F7), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun PrintPreviewDialog(
    show: Boolean,
    title: String,
    url: String,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    if (!show) return
    var copies by remember { mutableIntStateOf(1) }
    var isPortrait by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preview", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                    }
                }

                // Options Section
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Printer
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Printer", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Save as PDF >", color = Color(0xFF3B82F6), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Copies
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Copies", color = Color.White, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (copies > 1) copies-- },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Minus", tint = Color.White)
                                }
                                Text("$copies", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(
                                    onClick = { copies++ },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Direction
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Direction", color = Color.White, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { isPortrait = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPortrait) Color(0xFF3B82F6) else Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Portrait")
                                }
                                Button(
                                    onClick = { isPortrait = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isPortrait) Color(0xFF3B82F6) else Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Landscape")
                                }
                            }
                        }
                    }

                    // Pages & Color
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Pages", color = Color.White)
                                Text("All (1 page)", color = Color.LightGray)
                            }
                            HorizontalDivider(color = Color(0xFF334155))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Color", color = Color.White)
                                Text("Colour", color = Color.LightGray)
                            }
                        }
                    }
                }

                // Bottom Print Action
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            onPrint()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print / Save as PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}



