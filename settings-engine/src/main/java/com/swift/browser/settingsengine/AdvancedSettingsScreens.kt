package com.swift.browser.settingsengine

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class EngineSettingItem(
    val key: String,
    val title: String,
    val description: String,
    val defaultValue: Boolean
)

val engineSettingsMap = mapOf(
    "adblock" to listOf(
        EngineSettingItem("adblock_strict", "Strict Mode", "Block all intrusive elements aggressively", false),
        EngineSettingItem("adblock_trackers", "Block Trackers", "Prevent cross-site tracking", true),
        EngineSettingItem("adblock_popups", "Block Popups", "Automatically block popup windows", true),
        EngineSettingItem("adblock_auto_update", "Auto-update Filters", "Update filter lists in background", true)
    ),
    "ai" to listOf(
        EngineSettingItem("ai_assistant", "AI Assistant", "Enable on-page AI chat and tools", true),
        EngineSettingItem("ai_summarize", "Auto-summarize", "Offer to summarize long articles", false),
        EngineSettingItem("ai_context", "Context Awareness", "Allow AI to read current page content", true)
    ),
    "audio" to listOf(
        EngineSettingItem("audio_background", "Background Playback", "Continue playing audio when app is minimized", true),
        EngineSettingItem("audio_focus", "Audio Focus", "Pause when other apps play audio", true),
        EngineSettingItem("audio_high_res", "High-Res Audio", "Prioritize high-quality audio streams", false)
    ),
    "backup" to listOf(
        EngineSettingItem("backup_auto", "Auto Backup", "Backup bookmarks and settings daily", true),
        EngineSettingItem("backup_wifi", "Backup over Wi-Fi only", "Save mobile data", true),
        EngineSettingItem("backup_encrypt", "Encrypted Backups", "Secure backup files with encryption", true)
    ),
    "bookmark" to listOf(
        EngineSettingItem("bm_sync", "Sync Bookmarks", "Sync with Swift Cloud", false),
        EngineSettingItem("bm_thumbs", "Save Thumbnails", "Store page previews for bookmarks", true),
        EngineSettingItem("bm_duplicates", "Prevent Duplicates", "Warn when adding an existing bookmark", true)
    ),
    "browser" to listOf(
        EngineSettingItem("browser_js", "Enable JavaScript", "Allow sites to run scripts", true),
        EngineSettingItem("browser_dom", "DOM Storage", "Allow sites to store data locally", true),
        EngineSettingItem("browser_3p_cookies", "Third-party Cookies", "Allow cookies from other domains", false),
        EngineSettingItem("browser_dnt", "Do Not Track", "Send DNT header with requests", true)
    ),
    "code_editor" to listOf(
        EngineSettingItem("code_syntax", "Syntax Highlighting", "Colorize code by language", true),
        EngineSettingItem("code_wrap", "Word Wrap", "Wrap long lines of code", false),
        EngineSettingItem("code_numbers", "Line Numbers", "Show line numbers in editor", true)
    ),
    "database" to listOf(
        EngineSettingItem("db_wal", "WAL Mode", "Write-Ahead Logging for better concurrency", true),
        EngineSettingItem("db_vacuum", "Auto-vacuum", "Reclaim storage space automatically", true),
        EngineSettingItem("db_in_memory", "In-memory Cache", "Use RAM for faster queries", false)
    ),
    "developer_tools" to listOf(
        EngineSettingItem("dev_console", "Web Console", "Capture console.log messages", true),
        EngineSettingItem("dev_network", "Network Monitor", "Log network requests and responses", false),
        EngineSettingItem("dev_elements", "DOM Inspector", "Allow inspecting page elements", true)
    ),
    "desktop" to listOf(
        EngineSettingItem("desktop_force", "Force Desktop", "Always request desktop versions of sites", false),
        EngineSettingItem("desktop_viewport", "Override Viewport", "Ignore mobile viewport tags", true),
        EngineSettingItem("desktop_touch", "Simulate Touch", "Translate mouse events to touch on desktop sites", true)
    ),
    "download" to listOf(
        EngineSettingItem("dl_multithread", "Multi-thread Downloading", "Split files into chunks for speed", true),
        EngineSettingItem("dl_wifi", "Wi-Fi Only", "Pause downloads on cellular networks", false),
        EngineSettingItem("dl_resume", "Auto-resume", "Resume broken downloads automatically", true)
    ),
    "extension" to listOf(
        EngineSettingItem("ext_auto_update", "Auto-update Extensions", "Keep add-ons up to date", true),
        EngineSettingItem("ext_incognito", "Allow in Incognito", "Run extensions in private tabs", false),
        EngineSettingItem("ext_strict", "Strict Permissions", "Ask before granting extension permissions", true)
    ),
    "history" to listOf(
        EngineSettingItem("hist_track", "Track History", "Save visited pages", true),
        EngineSettingItem("hist_days", "Keep 90 Days", "Auto-delete older history", true),
        EngineSettingItem("hist_sync", "Sync History", "Sync across devices", false)
    ),
    "image" to listOf(
        EngineSettingItem("img_load", "Load Images", "Download and display images", true),
        EngineSettingItem("img_webp", "Prefer WebP", "Use WebP format when available for speed", true),
        EngineSettingItem("img_lazy", "Lazy Loading", "Only load images when scrolled into view", true)
    ),
    "media_detector" to listOf(
        EngineSettingItem("md_video", "Detect Videos", "Find downloadable video links on pages", true),
        EngineSettingItem("md_audio", "Detect Audio", "Find downloadable audio links", true),
        EngineSettingItem("md_m3u8", "HLS Stream Detection", "Detect streaming playlists", true),
        EngineSettingItem("md_sniff", "Network Sniffing", "Intercept network requests for media", false)
    ),
    "media" to listOf(
        EngineSettingItem("media_hw", "Hardware Decoding", "Use GPU for media decoding", true),
        EngineSettingItem("media_background", "Background Play", "Continue playing when switching tabs", false),
        EngineSettingItem("media_autoplay", "Autoplay", "Automatically start media playback", false)
    ),
    "native_download" to listOf(
        EngineSettingItem("ndl_accel", "Download Acceleration", "Use native C++ engine for speed", true),
        EngineSettingItem("ndl_alloc", "Pre-allocate Space", "Reserve disk space before downloading", true),
        EngineSettingItem("ndl_checksum", "Verify Checksums", "Check file integrity after download", true)
    ),
    "native_media" to listOf(
        EngineSettingItem("nm_ffmpeg", "FFmpeg Engine", "Use native FFmpeg for wide format support", true),
        EngineSettingItem("nm_gl", "OpenGL Rendering", "Render video frames using OpenGL", true),
        EngineSettingItem("nm_buffer", "Large Buffer", "Use larger memory buffers for smooth playback", false)
    ),
    "native_network" to listOf(
        EngineSettingItem("nnet_quic", "QUIC Protocol", "Use HTTP/3 QUIC for faster connections", true),
        EngineSettingItem("nnet_dns", "DoH", "DNS over HTTPS for privacy", false),
        EngineSettingItem("nnet_brotli", "Brotli Compression", "Use advanced compression if available", true)
    ),
    "native_security" to listOf(
        EngineSettingItem("nsec_tls13", "Enforce TLS 1.3", "Require modern encryption standards", false),
        EngineSettingItem("nsec_cert", "Strict Cert Checks", "Aggressively validate SSL certificates", true),
        EngineSettingItem("nsec_sandbox", "Native Sandbox", "Isolate engine processes", true)
    ),
    "network" to listOf(
        EngineSettingItem("net_prefetch", "DNS Prefetching", "Resolve domains ahead of time", true),
        EngineSettingItem("net_cache", "Network Cache", "Cache HTTP responses locally", true),
        EngineSettingItem("net_proxy", "Use System Proxy", "Respect system-wide proxy settings", true)
    ),
    "news" to listOf(
        EngineSettingItem("news_feed", "Show News Feed", "Display news on the new tab page", true),
        EngineSettingItem("news_personalize", "Personalized News", "Tailor news based on browsing history", false),
        EngineSettingItem("news_notifications", "Breaking News Alerts", "Send push notifications for major news", false)
    ),
    "notification" to listOf(
        EngineSettingItem("notif_sites", "Site Notifications", "Allow sites to send push notifications", true),
        EngineSettingItem("notif_downloads", "Download Alerts", "Show alerts for completed downloads", true),
        EngineSettingItem("notif_sound", "Play Sound", "Play sound for browser notifications", true)
    ),
    "permission" to listOf(
        EngineSettingItem("perm_location", "Location Access", "Allow sites to request location", true),
        EngineSettingItem("perm_camera", "Camera Access", "Allow sites to request camera", true),
        EngineSettingItem("perm_mic", "Microphone Access", "Allow sites to request microphone", true)
    ),
    "reader" to listOf(
        EngineSettingItem("read_dark", "Dark Theme", "Use dark background in reader mode", true),
        EngineSettingItem("read_font", "Large Fonts", "Use larger text for readability", false),
        EngineSettingItem("read_auto", "Auto Reader", "Automatically switch to reader mode on articles", false)
    ),
    "search" to listOf(
        EngineSettingItem("search_suggest", "Search Suggestions", "Show suggestions as you type", true),
        EngineSettingItem("search_local", "Local Results", "Include bookmarks and history in results", true),
        EngineSettingItem("search_secure", "Secure Search", "Force HTTPS for all search queries", true)
    ),
    "security" to listOf(
        EngineSettingItem("sec_safe_browsing", "Safe Browsing", "Protect against dangerous sites", true),
        EngineSettingItem("sec_https_only", "HTTPS-Only Mode", "Upgrade all connections to HTTPS", false),
        EngineSettingItem("sec_block_mixed", "Block Mixed Content", "Block insecure elements on secure pages", true),
        EngineSettingItem("purge_private_on_timeout_exit", "Auto-Purge Private Cache & Cookies", "Immediately purge private cache and cookies upon biometric timeout or app exit", true)
    ),
    "tab" to listOf(
        EngineSettingItem("tab_restore", "Restore Session", "Reopen tabs from previous session", true),
        EngineSettingItem("tab_groups", "Tab Groups", "Allow organizing tabs into groups", true),
        EngineSettingItem("tab_grid", "Grid Layout", "Show tabs in a grid view", true)
    ),
    "translate" to listOf(
        EngineSettingItem("trans_auto", "Auto-Translate", "Translate foreign pages automatically", false),
        EngineSettingItem("trans_offline", "Offline Translation", "Use downloaded language models", false),
        EngineSettingItem("trans_prompt", "Show Translation Prompt", "Ask to translate foreign pages", true)
    ),
    "data_saver" to listOf(
        EngineSettingItem("ds_image_compression", "Image Compression", "Compress images to WebP format to save bandwidth", true),
        EngineSettingItem("ds_block_scripts", "Block Heavy Scripts", "Prevent large JavaScript bundles from loading", false),
        EngineSettingItem("ds_smart_cache", "Smart Caching", "Aggressively cache static resources", true)
    ),
    "privacy_shield" to listOf(
        EngineSettingItem("ps_canvas_fingerprint", "Block Canvas Fingerprinting", "Prevent websites from identifying your device", true),
        EngineSettingItem("ps_webrtc_leak", "Prevent WebRTC Leaks", "Hide local IP addresses from websites", true),
        EngineSettingItem("ps_auto_clear", "Auto Clear Cookies", "Delete cookies when closing the tab", false),
        EngineSettingItem("purge_private_on_timeout_exit", "Auto-Purge Private Cache & Cookies", "Immediately purge private cache and cookies upon biometric timeout or app exit", true)
    ),
    "network_stats" to listOf(
        EngineSettingItem("ns_live_ping", "Live Ping Monitor", "Show current latency in developer dashboard", true),
        EngineSettingItem("ns_data_cap", "Data Usage Warnings", "Alert when approaching daily data limit", false),
        EngineSettingItem("ns_dns_timing", "DNS Timing Logs", "Record DNS resolution times", false)
    ),
    "battery_saver" to listOf(
        EngineSettingItem("bs_reduce_framerate", "Reduce Framerate", "Limit UI rendering to 30 FPS", false),
        EngineSettingItem("bs_suspend_tabs", "Suspend Background Tabs", "Freeze tabs after 5 minutes of inactivity", true),
        EngineSettingItem("bs_dark_theme_auto", "Auto Dark Mode", "Switch to dark theme to save OLED power", true)
    ),
    "weather" to listOf(
        EngineSettingItem("we_auto_location", "Auto Location", "Update weather based on GPS coordinates", true),
        EngineSettingItem("we_notifications", "Severe Alerts", "Notify for severe weather conditions", true),
        EngineSettingItem("we_metric", "Use Metric System", "Display temperatures in Celsius", true)
    ),
    "antivirus" to listOf(
        EngineSettingItem("av_realtime", "Real-Time Protection", "Scan downloaded files instantly", true),
        EngineSettingItem("av_cloud_scan", "Cloud Heuristics", "Use AI to identify zero-day threats", true),
        EngineSettingItem("av_auto_clean", "Auto Junk Cleaner", "Automatically remove temporary cache files", false)
    ),
    "video" to listOf(
        EngineSettingItem("vid_pip", "Auto PiP", "Enter Picture-in-Picture when leaving app", true),
        EngineSettingItem("vid_hw", "Hardware Acceleration", "Use GPU for video decoding", true),
        EngineSettingItem("vid_gestures", "Swipe Gestures", "Swipe for volume and brightness control", true)
    ),
    "password" to listOf(
        EngineSettingItem("pwd_autofill", "Credential Autofill", "Automatically offer to fill saved passwords on web forms", true),
        EngineSettingItem("pwd_autosave", "Auto-Save Passwords", "Ask to save credentials when logging in on sites", true),
        EngineSettingItem("pwd_master_lock", "Master Password Lock", "Require PIN or master password to view saved credentials", true),
        EngineSettingItem("pwd_audit_alerts", "Weak Password Alerts", "Notify if any stored password is weak or reused", true),
        EngineSettingItem("pwd_generator_symbols", "Generator Special Characters", "Include symbols (@#$%) when generating passwords", true)
    )
)

val enginesList = listOf(
    Triple("adblock", "AdBlock Engine", "Advanced ad and tracker blocking system"),
    Triple("ai", "AI Engine", "Artificial intelligence and smart features"),
    Triple("audio", "Audio Engine", "Background audio and music player controls"),
    Triple("backup", "Backup Engine", "Data synchronization and backup management"),
    Triple("bookmark", "Bookmark Engine", "Bookmark organization and syncing"),
    Triple("browser", "Browser Engine", "Core web rendering and page loading"),
    Triple("code_editor", "Code Editor Engine", "Source code viewing and text editing"),
    Triple("database", "Database Core", "Local data storage and persistence"),
    Triple("developer_tools", "Developer Tools Engine", "Web debugging, console, and DOM inspection"),
    Triple("desktop", "Desktop Engine", "Desktop site requesting and UA spoofing"),
    Triple("download", "Download Engine", "High-speed file downloading and queuing"),
    Triple("extension", "Extension Engine", "Browser add-ons and userscript support"),
    Triple("history", "History Engine", "Browsing history tracking and management"),
    Triple("image", "Image Engine", "Image caching, viewing, and processing"),
    Triple("media_detector", "Media Detector Engine", "Automatic video and audio link extraction"),
    Triple("media", "Media Engine", "General media processing and playback"),
    Triple("native_download", "Native Download Engine", "C/C++ optimized fast downloading"),
    Triple("native_media", "Native Media Engine", "C/C++ optimized media handling"),
    Triple("native_network", "Native Network Engine", "C/C++ high-performance networking"),
    Triple("native_security", "Native Security Engine", "C/C++ cryptography and encryption"),
    Triple("network", "Network Core", "HTTP requests and connection pooling"),
    Triple("news", "News Engine", "News feed aggregation and articles"),
    Triple("notification", "Notification Engine", "System alerts and push notifications"),
    Triple("password", "Password Engine", "Password manager, credential autofill, encryption & CSV/JSON import"),
    Triple("permission", "Permission Engine", "Site permissions and privacy controls"),
    Triple("reader", "Reader Engine", "Distraction-free reading mode"),
    Triple("search", "Search Engine Module", "Internal search query processing"),
    Triple("security", "Security Engine", "SSL validation and safe browsing checks"),
    Triple("tab", "Tab Engine", "Multi-tab management and session restore"),
    Triple("translate", "Translate Engine", "Real-time webpage translation"),
    Triple("data_saver", "Data Saver Engine", "Compresses images and blocks heavy scripts"),
    Triple("privacy_shield", "Privacy Shield", "Prevents canvas fingerprinting and tracking"),
    Triple("network_stats", "Network Stats", "Monitor ping, latency, and data usage"),
    Triple("battery_saver", "Battery Saver", "Reduces background activity to save power"),
    Triple("weather", "Weather Engine", "Real-time weather tracking and forecasting"),
    Triple("antivirus", "Antivirus & Cleaner", "Scans for malware and clears junk files"),
    Triple("video", "Video Engine", "Video player, PiP, and streaming")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    settingsEngine: SettingsEngine,
    onBack: () -> Unit,
    onNavigateToEngines: () -> Unit,
    onNavigateToPasswordManager: () -> Unit = {},
    onNavigateToLiveStreamSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Dialog state holders
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showHomepageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showReaderFontSizeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Dynamic State Values
    var currentSearchEngine by remember { mutableStateOf(settingsEngine.defaultSearchEngine) }
    var currentHomepageType by remember { mutableStateOf(settingsEngine.homepageType) }
    var currentTheme by remember { mutableStateOf(settingsEngine.appTheme) }
    var currentWallpaper by remember { mutableStateOf(settingsEngine.newTabWallpaper) }
    var currentReaderFontSize by remember { mutableIntStateOf(settingsEngine.readerFontSize) }
    var currentLanguage by remember { mutableStateOf(settingsEngine.targetLanguage) }

    // Toggles
    var showHomeButton by remember { mutableStateOf(settingsEngine.showHomeButton) }
    var httpsOnlyMode by remember { mutableStateOf(settingsEngine.isHttpsOnlyMode) }
    var safeBrowsing by remember { mutableStateOf(settingsEngine.isSafeBrowsingEnabled) }
    var dntEnabled by remember { mutableStateOf(settingsEngine.isDoNotTrackEnabled) }
    var blockThirdPartyCookies by remember { mutableStateOf(settingsEngine.isBlockThirdPartyCookies) }
    var canvasFingerprint by remember { mutableStateOf(settingsEngine.isCanvasFingerprintProtected) }
    var webRtcLeak by remember { mutableStateOf(settingsEngine.isWebRtcLeakProtected) }
    var clearOnExit by remember { mutableStateOf(settingsEngine.isClearDataOnExit) }
    var purgePrivateOnTimeoutOrExit by remember { mutableStateOf(settingsEngine.isPurgePrivateOnTimeoutOrExit) }

    var adBlockEnabled by remember { mutableStateOf(settingsEngine.isAdBlockEnabled) }
    var blockTrackers by remember { mutableStateOf(settingsEngine.isBlockTrackers) }
    var blockPopups by remember { mutableStateOf(settingsEngine.isBlockPopups) }
    var autoUpdateFilters by remember { mutableStateOf(settingsEngine.isAutoUpdateFilterLists) }

    var dlWifiOnly by remember { mutableStateOf(settingsEngine.isDownloadWifiOnly) }
    var askBeforeDownload by remember { mutableStateOf(settingsEngine.isAskBeforeDownload) }
    var multithreadDl by remember { mutableStateOf(settingsEngine.isMultithreadedDownload) }
    var dlAlerts by remember { mutableStateOf(settingsEngine.isDownloadAlertsEnabled) }

    var jsEnabled by remember { mutableStateOf(settingsEngine.isJavaScriptEnabled) }
    var hwAccEnabled by remember { mutableStateOf(settingsEngine.isHardwareAccelerationEnabled) }
    var domStorage by remember { mutableStateOf(settingsEngine.isDomStorageEnabled) }
    var bgAudio by remember { mutableStateOf(settingsEngine.isBackgroundAudioEnabled) }
    var autoPip by remember { mutableStateOf(settingsEngine.isAutoPipEnabled) }
    var mediaSniffer by remember { mutableStateOf(settingsEngine.isMediaSnifferEnabled) }

    var autoTranslate by remember { mutableStateOf(settingsEngine.isAutoTranslateEnabled) }
    var translatePrompt by remember { mutableStateOf(settingsEngine.isTranslatePromptEnabled) }

    var aiAssistant by remember { mutableStateOf(settingsEngine.isAiAssistantEnabled) }
    var aiSummarize by remember { mutableStateOf(settingsEngine.isAiAutoSummarize) }
    var aiContext by remember { mutableStateOf(settingsEngine.isAiContextAware) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input Bar
            AnimatedVisibility(visible = isSearchActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search all settings & options...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Section: General
                if (searchQuery.isEmpty() || "general search homepage address home".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("General")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Search,
                            title = "Search engine",
                            subtitle = currentSearchEngine,
                            onClick = { showSearchEngineDialog = true }
                        )
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Home,
                            title = "Homepage",
                            subtitle = if (currentHomepageType == "ntp") "New Tab Page" else settingsEngine.homepageCustomUrl,
                            onClick = { showHomepageDialog = true }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.HomeMini,
                            title = "Show Home button",
                            subtitle = "Display quick home icon on the browser toolbar",
                            checked = showHomeButton,
                            onCheckedChange = {
                                showHomeButton = it
                                settingsEngine.showHomeButton = it
                            }
                        )
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.ViewAgenda,
                            title = "Address bar position",
                            subtitle = if (settingsEngine.addressBarPosition == "bottom") "Bottom" else "Top",
                            onClick = {
                                val newPos = if (settingsEngine.addressBarPosition == "bottom") "top" else "bottom"
                                settingsEngine.addressBarPosition = newPos
                                Toast.makeText(context, "Address bar set to $newPos", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Appearance & Customization
                if (searchQuery.isEmpty() || "appearance theme dark light wallpaper reader font display".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Appearance & Customization")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Palette,
                            title = "Theme",
                            subtitle = currentTheme,
                            onClick = { showThemeDialog = true }
                        )
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Wallpaper,
                            title = "New Tab wallpaper",
                            subtitle = currentWallpaper,
                            onClick = { showWallpaperDialog = true }
                        )
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.FormatSize,
                            title = "Reading Mode font size",
                            subtitle = "${currentReaderFontSize}sp",
                            onClick = { showReaderFontSizeDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Passwords & Vault
                if (searchQuery.isEmpty() || "password credentials autofill import export login vault key".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Passwords & Vault")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Key,
                            title = "Password Manager & Vault",
                            subtitle = "View, manage, search, encrypt, and import saved website credentials",
                            onClick = onNavigateToPasswordManager
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.AutoMode,
                            title = "Credential Autofill",
                            subtitle = "Prompt to automatically fill usernames and passwords on web logins",
                            checked = settingsEngine.getBoolean("pwd_autofill", true),
                            onCheckedChange = {
                                settingsEngine.setBoolean("pwd_autofill", it)
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Save,
                            title = "Auto-save passwords",
                            subtitle = "Offer to save login credentials when signing in to websites",
                            checked = settingsEngine.getBoolean("pwd_autosave", true),
                            onCheckedChange = {
                                settingsEngine.setBoolean("pwd_autosave", it)
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Lock,
                            title = "Master Lock Protection",
                            subtitle = "Protect sensitive vault items behind master password or PIN",
                            checked = settingsEngine.getBoolean("pwd_master_lock", true),
                            onCheckedChange = {
                                settingsEngine.setBoolean("pwd_master_lock", it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Privacy & Security
                if (searchQuery.isEmpty() || "privacy security https ssl track dnt cookie canvas webrtc clear".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Privacy & Security")
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Security,
                            title = "Safe Browsing",
                            subtitle = "Protects your device against dangerous malware and phishing domains",
                            checked = safeBrowsing,
                            onCheckedChange = {
                                safeBrowsing = it
                                settingsEngine.isSafeBrowsingEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Https,
                            title = "HTTPS-Only Mode",
                            subtitle = "Automatically upgrade connections to secure HTTPS and warn before unencrypted sites",
                            checked = httpsOnlyMode,
                            onCheckedChange = {
                                httpsOnlyMode = it
                                settingsEngine.isHttpsOnlyMode = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.DoNotDisturb,
                            title = "Do Not Track (DNT)",
                            subtitle = "Send a 'Do Not Track' request with your browsing traffic",
                            checked = dntEnabled,
                            onCheckedChange = {
                                dntEnabled = it
                                settingsEngine.isDoNotTrackEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Cookie,
                            title = "Block third-party cookies",
                            subtitle = "Prevent sites and advertisers from tracking you across the web",
                            checked = blockThirdPartyCookies,
                            onCheckedChange = {
                                blockThirdPartyCookies = it
                                settingsEngine.isBlockThirdPartyCookies = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Shield,
                            title = "Canvas & WebRTC leak shield",
                            subtitle = "Disguise hardware fingerprinting and prevent local IP leaks",
                            checked = canvasFingerprint && webRtcLeak,
                            onCheckedChange = {
                                canvasFingerprint = it
                                webRtcLeak = it
                                settingsEngine.isCanvasFingerprintProtected = it
                                settingsEngine.isWebRtcLeakProtected = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.ExitToApp,
                            title = "Clear data on exit",
                            subtitle = "Automatically delete cookies and cache when you close Swift Browser",
                            checked = clearOnExit,
                            onCheckedChange = {
                                clearOnExit = it
                                settingsEngine.isClearDataOnExit = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Lock,
                            title = "Purge private cache & cookies on timeout/exit",
                            subtitle = "Automatically purge all private mode cache and cookies immediately upon biometric timeout or application exit",
                            checked = purgePrivateOnTimeoutOrExit,
                            onCheckedChange = {
                                purgePrivateOnTimeoutOrExit = it
                                settingsEngine.isPurgePrivateOnTimeoutOrExit = it
                            }
                        )
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.DeleteSweep,
                            title = "Clear browsing data",
                            subtitle = "Clear history, cookies, cache, site storage and form autofill",
                            onClick = { showClearDataDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: AdBlock & Content Filtering
                if (searchQuery.isEmpty() || "adblock ad filter tracker popup shield".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("AdBlock & Content Filtering")
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Block,
                            title = "AdBlocker master engine",
                            subtitle = "Block intrusive video and banner ads across all web pages",
                            checked = adBlockEnabled,
                            onCheckedChange = {
                                adBlockEnabled = it
                                settingsEngine.isAdBlockEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.TrackChanges,
                            title = "Block invasive trackers",
                            subtitle = "Filter out analytics and cross-domain tracking scripts",
                            checked = blockTrackers,
                            enabled = adBlockEnabled,
                            onCheckedChange = {
                                blockTrackers = it
                                settingsEngine.isBlockTrackers = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.OpenInNew,
                            title = "Block popup windows",
                            subtitle = "Prevent unwanted redirect tabs and dialog popups",
                            checked = blockPopups,
                            enabled = adBlockEnabled,
                            onCheckedChange = {
                                blockPopups = it
                                settingsEngine.isBlockPopups = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Sync,
                            title = "Auto-update filter lists",
                            subtitle = "Keep EasyList and privacy rules automatically up to date",
                            checked = autoUpdateFilters,
                            enabled = adBlockEnabled,
                            onCheckedChange = {
                                autoUpdateFilters = it
                                settingsEngine.isAutoUpdateFilterLists = it
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Downloads & Storage
                if (searchQuery.isEmpty() || "download wifi multithread file storage path".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Downloads & Storage")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Folder,
                            title = "Download location",
                            subtitle = settingsEngine.downloadDirectory,
                            onClick = {
                                Toast.makeText(context, "Storage set to default Downloads folder", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Wifi,
                            title = "Download over Wi-Fi only",
                            subtitle = "Pause downloads when using cellular mobile data",
                            checked = dlWifiOnly,
                            onCheckedChange = {
                                dlWifiOnly = it
                                settingsEngine.isDownloadWifiOnly = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.QuestionMark,
                            title = "Ask where to save files",
                            subtitle = "Prompt for filename and location before starting downloads",
                            checked = askBeforeDownload,
                            onCheckedChange = {
                                askBeforeDownload = it
                                settingsEngine.isAskBeforeDownload = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Speed,
                            title = "Multi-threaded download acceleration",
                            subtitle = "Accelerate download speed using parallel segmented chunks",
                            checked = multithreadDl,
                            onCheckedChange = {
                                multithreadDl = it
                                settingsEngine.isMultithreadedDownload = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Notifications,
                            title = "Download completion alerts",
                            subtitle = "Show system notifications when downloads finish",
                            checked = dlAlerts,
                            onCheckedChange = {
                                dlAlerts = it
                                settingsEngine.isDownloadAlertsEnabled = it
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Live Streaming & Broadcasting
                if (searchQuery.isEmpty() || "live streaming youtube rtmp rtmps broadcast broadcast setup stream key".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Live Streaming & Broadcasting")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.LiveTv,
                            title = "Secure Streaming Setup",
                            subtitle = "Configure secure YouTube or custom RTMP/RTMPS endpoints and stream keys",
                            onClick = onNavigateToLiveStreamSettings
                        )
                    }
                }

                // Section: Web & Media Playback
                if (searchQuery.isEmpty() || "web media javascript audio video pip dom hardware acceleration sniffer".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Web & Media Playback")
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Javascript,
                            title = "JavaScript",
                            subtitle = "Enable JavaScript script execution for modern interactive web pages",
                            checked = jsEnabled,
                            onCheckedChange = {
                                jsEnabled = it
                                settingsEngine.isJavaScriptEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Memory,
                            title = "Hardware Acceleration",
                            subtitle = "Use GPU graphics rendering for smoother 60fps scrolling and animations",
                            checked = hwAccEnabled,
                            onCheckedChange = {
                                hwAccEnabled = it
                                settingsEngine.isHardwareAccelerationEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Storage,
                            title = "DOM Storage",
                            subtitle = "Allow web applications to store client-side local database data",
                            checked = domStorage,
                            onCheckedChange = {
                                domStorage = it
                                settingsEngine.isDomStorageEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Headphones,
                            title = "Background Audio Playback",
                            subtitle = "Continue playing web and video audio when switching tabs or locking screen",
                            checked = bgAudio,
                            onCheckedChange = {
                                bgAudio = it
                                settingsEngine.isBackgroundAudioEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.PictureInPicture,
                            title = "Auto Picture-in-Picture (PiP)",
                            subtitle = "Seamlessly float video player when leaving full screen or pressing Home",
                            checked = autoPip,
                            onCheckedChange = {
                                autoPip = it
                                settingsEngine.isAutoPipEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.VideoLibrary,
                            title = "Media Link Detector",
                            subtitle = "Automatically detect streaming videos and direct media links for 1-tap download",
                            checked = mediaSniffer,
                            onCheckedChange = {
                                mediaSniffer = it
                                settingsEngine.isMediaSnifferEnabled = it
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Languages & Translation
                if (searchQuery.isEmpty() || "translate translation language foreign hindi spanish".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Languages & Translation")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Translate,
                            title = "Target translation language",
                            subtitle = currentLanguage,
                            onClick = { showLanguageDialog = true }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.AutoMode,
                            title = "Auto-translate foreign sites",
                            subtitle = "Translate pages not in your preferred language without asking",
                            checked = autoTranslate,
                            onCheckedChange = {
                                autoTranslate = it
                                settingsEngine.isAutoTranslateEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.ChatBubbleOutline,
                            title = "Show translation bar",
                            subtitle = "Offer translation options when viewing foreign language pages",
                            checked = translatePrompt,
                            onCheckedChange = {
                                translatePrompt = it
                                settingsEngine.isTranslatePromptEnabled = it
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: AI Assistant & Smart Features
                if (searchQuery.isEmpty() || "ai assistant summarize smart gemini intelligence".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Swift AI & Smart Assistant")
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.AutoAwesome,
                            title = "Swift AI Assistant",
                            subtitle = "Integrated intelligent sidebar for page comprehension, questions, and writing",
                            checked = aiAssistant,
                            onCheckedChange = {
                                aiAssistant = it
                                settingsEngine.isAiAssistantEnabled = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Summarize,
                            title = "Auto-summarize long articles",
                            subtitle = "Offer one-tap bullet point summaries for long news articles and blogs",
                            checked = aiSummarize,
                            enabled = aiAssistant,
                            onCheckedChange = {
                                aiSummarize = it
                                settingsEngine.isAiAutoSummarize = it
                            }
                        )
                    }
                    item {
                        SettingsItemSwitch(
                            icon = Icons.Default.Psychology,
                            title = "Page context awareness",
                            subtitle = "Allow AI Assistant to analyze active webpage content for accurate answers",
                            checked = aiContext,
                            enabled = aiAssistant,
                            onCheckedChange = {
                                aiContext = it
                                settingsEngine.isAiContextAware = it
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: Advanced Engine Architecture
                if (searchQuery.isEmpty() || "engine architecture developer native cpp debug backup database".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("Internal Architecture & Engines")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.DeveloperMode,
                            title = "Manage Browser Engines",
                            subtitle = "Fine-tune 35+ modular C++ & Kotlin browser components and diagnostics",
                            onClick = onNavigateToEngines
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                // Section: System & Reset
                if (searchQuery.isEmpty() || "reset about version defaults info license".contains(searchQuery, ignoreCase = true)) {
                    item {
                        SettingsCategoryHeader("System & Reset")
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Restore,
                            title = "Reset all settings to defaults",
                            subtitle = "Revert browser preferences and engine toggles to factory state",
                            onClick = { showResetDialog = true }
                        )
                    }
                    item {
                        SettingsItemClickable(
                            icon = Icons.Default.Info,
                            title = "About Swift Browser",
                            subtitle = "Version 1.0.0 (Chromium WebKit Core • Architecture v34)",
                            onClick = { showAboutDialog = true }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // --- DIALOGS ---

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        val engines = listOf("Google", "DuckDuckGo", "Bing", "Yahoo", "Baidu", "Ecosia")
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Default Search Engine") },
            text = {
                Column {
                    engines.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSearchEngine = engine
                                    settingsEngine.defaultSearchEngine = engine
                                    showSearchEngineDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentSearchEngine == engine),
                                onClick = {
                                    currentSearchEngine = engine
                                    settingsEngine.defaultSearchEngine = engine
                                    showSearchEngineDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(engine, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Homepage Selection Dialog
    if (showHomepageDialog) {
        var selectedType by remember { mutableStateOf(currentHomepageType) }
        var customUrl by remember { mutableStateOf(settingsEngine.homepageCustomUrl) }
        AlertDialog(
            onDismissRequest = { showHomepageDialog = false },
            title = { Text("Homepage") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = "ntp" }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (selectedType == "ntp"), onClick = { selectedType = "ntp" })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Tab Page (Default)")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = "custom" }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (selectedType == "custom"), onClick = { selectedType = "custom" })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Web Address")
                    }

                    if (selectedType == "custom") {
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            label = { Text("Enter URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    currentHomepageType = selectedType
                    settingsEngine.homepageType = selectedType
                    if (selectedType == "custom") {
                        settingsEngine.homepageCustomUrl = customUrl
                    }
                    showHomepageDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHomepageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Theme Mode Dialog
    if (showThemeDialog) {
        val themes = listOf(
            Triple("System", "System Default", "Automatically adapt to device light / dark setting"),
            Triple("Light", "Light Mode", "Crisp daylight clarity with bright surfaces"),
            Triple("Dark", "Dark Mode", "Deep charcoal theme designed for night-time comfort"),
            Triple("Contrast", "High Contrast", "High legibility & enhanced border visibility")
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme Mode", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    themes.forEach { (themeId, title, desc) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    currentTheme = themeId
                                    settingsEngine.appTheme = themeId
                                    showThemeDialog = false
                                },
                            color = if (currentTheme == themeId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentTheme == themeId),
                                    onClick = {
                                        currentTheme = themeId
                                        settingsEngine.appTheme = themeId
                                        showThemeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Wallpaper Selection Dialog
    if (showWallpaperDialog) {
        val wallpapers = listOf("Frosted Glass", "Deep Space", "Gradient Dawn", "Cyberpunk", "Midnight Minimal")
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("New Tab Wallpaper") },
            text = {
                Column {
                    wallpapers.forEach { wp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentWallpaper = wp
                                    settingsEngine.newTabWallpaper = wp
                                    showWallpaperDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentWallpaper == wp),
                                onClick = {
                                    currentWallpaper = wp
                                    settingsEngine.newTabWallpaper = wp
                                    showWallpaperDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(wp, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Reader Font Size Dialog
    if (showReaderFontSizeDialog) {
        var sliderValue by remember { mutableFloatStateOf(currentReaderFontSize.toFloat()) }
        AlertDialog(
            onDismissRequest = { showReaderFontSizeDialog = false },
            title = { Text("Reading Mode Font Size") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${sliderValue.toInt()} sp", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 12f..28f,
                        steps = 7
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Text(
                            text = "Sample reading text preview in Swift Browser.",
                            fontSize = sliderValue.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    currentReaderFontSize = sliderValue.toInt()
                    settingsEngine.readerFontSize = sliderValue.toInt()
                    showReaderFontSizeDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReaderFontSizeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        val languages = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese", "Chinese", "Russian", "Arabic", "Portuguese")
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Target Translation Language") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(languages) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentLanguage = lang
                                    settingsEngine.targetLanguage = lang
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentLanguage == lang),
                                onClick = {
                                    currentLanguage = lang
                                    settingsEngine.targetLanguage = lang
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Data Dialog
    if (showClearDataDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearCookies by remember { mutableStateOf(true) }
        var clearCache by remember { mutableStateOf(true) }
        var clearStorage by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Browsing Data") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { clearHistory = !clearHistory }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Browsing history")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { clearCookies = !clearCookies }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Cookies and site data")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { clearCache = !clearCache }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Cached images and files")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { clearStorage = !clearStorage }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearStorage, onCheckedChange = { clearStorage = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Site storage & permissions")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            if (clearCookies) {
                                com.swift.browser.cookieengine.CookieEngineApi.getInstance(context).removeAllCookies()
                            }
                            if (clearStorage) {
                                android.webkit.WebStorage.getInstance().deleteAllData()
                            }
                            if (clearCache) {
                                android.webkit.WebView(context).clearCache(true)
                                context.cacheDir.deleteRecursively()
                            }
                            if (clearHistory) {
                                val intent = android.content.Intent("com.swift.browser.ACTION_CLEAR_HISTORY")
                                context.sendBroadcast(intent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        Toast.makeText(context, "Selected browsing data cleared", Toast.LENGTH_SHORT).show()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Settings Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all settings?") },
            text = { Text("This will restore default browser settings and engine configurations. Your bookmarks and browsing history will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        settingsEngine.resetToDefaults()
                        enginesList.forEach { settingsEngine.setBoolean("${it.first}_engine_enabled", true) }
                        engineSettingsMap.values.flatten().forEach { settingsEngine.setBoolean(it.key, it.defaultValue) }
                        currentSearchEngine = "Google"
                        currentHomepageType = "ntp"
                        currentTheme = "System"
                        currentWallpaper = "Frosted Glass"
                        currentReaderFontSize = 16
                        currentLanguage = "English"
                        showHomeButton = true
                        httpsOnlyMode = false
                        safeBrowsing = true
                        dntEnabled = true
                        blockThirdPartyCookies = false
                        canvasFingerprint = true
                        webRtcLeak = true
                        clearOnExit = false
                        adBlockEnabled = true
                        blockTrackers = true
                        blockPopups = true
                        autoUpdateFilters = true
                        dlWifiOnly = false
                        askBeforeDownload = false
                        multithreadDl = true
                        dlAlerts = true
                        jsEnabled = true
                        hwAccEnabled = true
                        domStorage = true
                        bgAudio = true
                        autoPip = true
                        mediaSniffer = true
                        autoTranslate = false
                        translatePrompt = true
                        aiAssistant = true
                        aiSummarize = false
                        aiContext = true
                        Toast.makeText(context, "All settings reset to factory defaults", Toast.LENGTH_SHORT).show()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Swift Browser Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Swift Browser")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Version 1.0.0 (Release Build 34)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Modern, high-performance web browser featuring 35+ modular engines, AI integration, AdBlock, background media playback, real-time translation, and advanced privacy shields.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("• Rendering: Android Chromium WebKit\n• Engine Core: Swift Modular Engine v34\n• License: Open Source / Apache 2.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsItemClickable(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsItemSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        } else null,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedEngineSettingsScreen(
    settingsEngine: SettingsEngine,
    onBack: () -> Unit,
    onNavigateToEngine: (String, String) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browser Engines", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Reset All Engine Settings") },
                    supportingContent = { Text("Revert all 35+ engines to factory defaults without affecting user data", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        enginesList.forEach { settingsEngine.setBoolean("${it.first}_engine_enabled", true) }
                        engineSettingsMap.values.flatten().forEach { settingsEngine.setBoolean(it.key, it.defaultValue) }
                        Toast.makeText(context, "All engines reset to factory defaults", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    text = "Manage Internal Modular Engines",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            items(enginesList) { (id, name, desc) ->
                var isEnabled by remember { mutableStateOf(settingsEngine.getBoolean("${id}_engine_enabled", true)) }
                ListItem(
                    headlineContent = { Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    isEnabled = it
                                    settingsEngine.setBoolean("${id}_engine_enabled", it)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = "Configure", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    modifier = Modifier.clickable {
                        onNavigateToEngine(id, name)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineDetailSettingsScreen(
    settingsEngine: SettingsEngine,
    engineId: String,
    engineName: String,
    onBack: () -> Unit
) {
    val settingsList = engineSettingsMap[engineId] ?: emptyList()

    // Master toggle for the engine itself
    var masterEnabled by remember { mutableStateOf(settingsEngine.getBoolean("${engineId}_engine_enabled", true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(engineName, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Enable Engine", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Master switch for $engineName features", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = {
                                masterEnabled = it
                                settingsEngine.setBoolean("${engineId}_engine_enabled", it)
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        masterEnabled = !masterEnabled
                        settingsEngine.setBoolean("${engineId}_engine_enabled", masterEnabled)
                    }
                )
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            }

            if (settingsList.isEmpty()) {
                item {
                    Text(
                        text = "No additional advanced settings available for this engine.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                item {
                    Text(
                        text = "Engine Specific Configurations",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                items(settingsList) { setting ->
                    var enabled by remember { mutableStateOf(settingsEngine.getBoolean(setting.key, setting.defaultValue)) }
                    ListItem(
                        headlineContent = { Text(setting.title, fontSize = 14.sp) },
                        supportingContent = { Text(setting.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    settingsEngine.setBoolean(setting.key, it)
                                },
                                enabled = masterEnabled
                            )
                        },
                        modifier = Modifier.clickable(enabled = masterEnabled) {
                            enabled = !enabled
                            settingsEngine.setBoolean(setting.key, enabled)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
