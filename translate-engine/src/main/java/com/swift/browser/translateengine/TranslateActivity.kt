package com.swift.browser.translateengine

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.translateengine.ui.TranslateLanguageDialog
import kotlinx.coroutines.launch

class TranslateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = TranslateEngineApi.getInstance(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TranslateSettingsScreen(
                        engine = engine,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSettingsScreen(
    engine: TranslateEngineApi,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var autoTranslate by remember { mutableStateOf(engine.settings.isAutoTranslateEnabled()) }
    var offerTranslate by remember { mutableStateOf(engine.settings.isOfferToTranslateEnabled()) }
    var defaultTargetLang by remember { mutableStateOf(engine.settings.getDefaultTargetLanguage()) }
    var defaultTargetLangName by remember { mutableStateOf(engine.languageManager.getLanguageDisplayName(defaultTargetLang)) }
    var cacheCount by remember { mutableIntStateOf(0) }
    var showLangDialog by remember { mutableStateOf(false) }

    val refreshCacheCount = {
        scope.launch {
            try {
                cacheCount = engine.repository.getCacheCount()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        refreshCacheCount()
    }

    if (showLangDialog) {
        TranslateLanguageDialog(
            languageManager = engine.languageManager,
            onTranslate = { langCode ->
                defaultTargetLang = langCode
                defaultTargetLangName = engine.languageManager.getLanguageDisplayName(langCode)
                engine.settings.setDefaultTargetLanguage(langCode)
                showLangDialog = false
                Toast.makeText(context, "Default language set to $defaultTargetLangName", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLangDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Translate Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Language Target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Active: $defaultTargetLangName ($defaultTargetLang)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { showLangDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Target Language")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Automation & Policies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offer to Translate Pages", fontWeight = FontWeight.Medium)
                                Text("Show translation prompts when a foreign page is detected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = offerTranslate,
                                onCheckedChange = {
                                    offerTranslate = it
                                    engine.settings.setOfferToTranslateEnabled(it)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Always Auto-Translate", fontWeight = FontWeight.Medium)
                                Text("Automatically translate foreign language web content", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoTranslate,
                                onCheckedChange = {
                                    autoTranslate = it
                                    engine.settings.setAutoTranslateEnabled(it)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Offline Database & Cache", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Cached phrase pairs: $cacheCount", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        engine.repository.pruneCache(7 * 24 * 60 * 60 * 1000L)
                                        refreshCacheCount()
                                        Toast.makeText(context, "Old cache entries pruned", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prune Expired")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        engine.repository.clearCache()
                                        refreshCacheCount()
                                        Toast.makeText(context, "Translation cache cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All Cache")
                            }
                        }
                    }
                }
            }

            val neverSites = engine.settings.getNeverTranslateSites().toList()
            if (neverSites.isNotEmpty()) {
                item {
                    Text("Never Translate Sites (${neverSites.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(neverSites) { site ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(site, fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = {
                                    engine.settings.removeNeverTranslateSite(site)
                                    Toast.makeText(context, "Removed $site", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
