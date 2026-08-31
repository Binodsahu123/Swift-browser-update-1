package com.swift.browser.downloaduiengine

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pagesCount by remember { mutableIntStateOf(0) }
    val renderedPages = remember { mutableStateListOf<Bitmap?>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                errorMessage = "File does not exist."
                return@LaunchedEffect
            }

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pagesCount = renderer.pageCount
            
            // Expand list
            renderedPages.clear()
            for (i in 0 until pagesCount) {
                renderedPages.add(null)
            }

            // Load and render pages in background
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                for (i in 0 until pagesCount) {
                    val page = renderer.openPage(i)
                    // Scale bitmap up for resolution
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (i < renderedPages.size) {
                            renderedPages[i] = bitmap
                        }
                    }
                }
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            Log.e("PdfViewerScreen", "Error rendering pdf", e)
            errorMessage = "Failed to render PDF document: ${e.localizedMessage}"
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F14))
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { innerPadding ->
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(errorMessage!!, color = Color.Red, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0A0A0F)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(renderedPages) { index, bitmap ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFF2E2E))
                            }
                        }
                    }
                    Text(
                        text = "Page ${index + 1} of $pagesCount",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
