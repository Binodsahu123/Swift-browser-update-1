package com.swift.browser.translateengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.translateengine.TranslateLanguageManager

@Composable
fun TranslateLanguageDialog(
    languageManager: TranslateLanguageManager? = null,
    onTranslate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveManager = remember(languageManager, context) {
        languageManager ?: TranslateLanguageManager(context)
    }

    val languages = remember(effectiveManager) {
        effectiveManager.availableLanguages.map { name ->
            val code = effectiveManager.getLanguageCode(name)
            name to code
        }.sortedBy { it.first }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, languages) {
        if (searchQuery.isBlank()) {
            languages
        } else {
            languages.filter {
                it.first.contains(searchQuery, ignoreCase = true) ||
                        it.second.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Web Translate", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Translate current web page dynamically. Choose from supported languages:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(filteredLanguages) { (langName, code) ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = langName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Code: $code",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTranslate(code) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}

@Composable
fun TranslateDialog(
    languageManager: TranslateLanguageManager? = null,
    onTranslate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TranslateLanguageDialog(
        languageManager = languageManager,
        onTranslate = onTranslate,
        onDismiss = onDismiss
    )
}

