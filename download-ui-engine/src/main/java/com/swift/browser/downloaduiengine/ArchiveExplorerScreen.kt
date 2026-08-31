package com.swift.browser.downloaduiengine

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.launch

data class ArchiveEntryModel(
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val size: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveExplorerScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var entriesList by remember { mutableStateOf<List<ArchiveEntryModel>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                errorMessage = "Archive file does not exist."
                return@LaunchedEffect
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ZipFile(file).use { zip ->
                    val list = zip.entries().asSequence().map { entry ->
                        ArchiveEntryModel(
                            name = entry.name,
                            isDirectory = entry.isDirectory,
                            compressedSize = entry.compressedSize,
                            size = entry.size
                        )
                    }.toList()

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        entriesList = list
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ArchiveExplorerScreen", "Error reading zip", e)
            errorMessage = "Failed to parse archive: ${e.localizedMessage}"
        }
    }

    fun extractArchive() {
        isExtracting = true
        errorMessage = null

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val file = File(filePath)
                val destDir = File(file.parentFile, file.nameWithoutExtension + "_extracted")
                if (!destDir.exists()) destDir.mkdirs()

                ZipFile(file).use { zip ->
                    for (entry in zip.entries()) {
                        val outFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isExtracting = false
                    Toast.makeText(context, "Successfully extracted to:\n${destDir.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ArchiveExplorerScreen", "Extraction failed", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isExtracting = false
                    errorMessage = "Extraction failed: ${e.localizedMessage}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (entriesList.isNotEmpty() && !isExtracting) {
                        IconButton(onClick = { extractArchive() }) {
                            Icon(Icons.Default.Unarchive, contentDescription = "Extract All", tint = Color(0xFFFF2E2E))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F14))
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExtracting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E28))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF2E2E), modifier = Modifier.size(20.dp))
                        Text("Extracting files natively...", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(errorMessage!!, color = Color.Red, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entriesList) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Default.FolderZip else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) Color(0xFFFF2E2E) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!entry.isDirectory) {
                                        Text(
                                            text = "Size: ${entry.size / 1024} KB (Compressed: ${entry.compressedSize / 1024} KB)",
                                            color = Color.Gray,
                                            fontSize = 11.sp
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
