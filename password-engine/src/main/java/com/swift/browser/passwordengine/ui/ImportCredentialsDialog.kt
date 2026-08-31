package com.swift.browser.passwordengine.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.passwordengine.importexport.ImportSummary

@Composable
fun ImportCredentialsDialog(
    onDismiss: () -> Unit,
    onImportUri: (Uri, (ImportSummary) -> Unit) -> Unit,
    onImportText: (String, (ImportSummary) -> Unit) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var pastedText by remember { mutableStateOf("") }
    var importSummary by remember { mutableStateOf<ImportSummary?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            onImportUri(uri) { summary ->
                isImporting = false
                importSummary = summary
            }
        }
    }

    if (importSummary != null) {
        val summary = importSummary!!
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (summary.importedCount > 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (summary.importedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Complete")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Parsed Entries: ${summary.totalParsed}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Successfully Imported: ${summary.importedCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (summary.skippedCount > 0) {
                                Text(
                                    text = "Skipped Entries: ${summary.skippedCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    if (summary.errors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Import Log / Warnings:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        summary.errors.forEach { err ->
                            Text(
                                text = "• $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Credentials")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Import passwords exported from Chrome, Edge, Firefox, Bitwarden, 1Password, or standard CSV/JSON files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("CSV / File") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Paste Text") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedTabIndex == 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Select a .csv or .json credential file from storage",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    filePickerLauncher.launch("*/*")
                                },
                                enabled = !isImporting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isImporting) "Importing..." else "Browse File")
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = pastedText,
                                onValueChange = { pastedText = it },
                                label = { Text("Paste CSV or JSON content") },
                                placeholder = { Text("url,username,password\nhttps://example.com,user@site.com,P@ssw0rd123") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                maxLines = 8,
                                singleLine = false
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (pastedText.isNotBlank()) {
                                        isImporting = true
                                        onImportText(pastedText) { summary ->
                                            isImporting = false
                                            importSummary = summary
                                        }
                                    }
                                },
                                enabled = pastedText.isNotBlank() && !isImporting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isImporting) "Importing..." else "Import Text")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}
