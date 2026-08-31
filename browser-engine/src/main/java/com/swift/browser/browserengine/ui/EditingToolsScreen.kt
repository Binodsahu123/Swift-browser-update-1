package com.swift.browser.browserengine.ui
import com.swift.browser.browserengine.BrowserViewModel
import com.swift.browser.webstudio.WebStudioActivity
import com.swift.browser.audioengine.AudioPlayerActivity


import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Assuming WebStudioActivity will be created in :web-studio and has fully qualified name com.swift.browser.webstudio.WebStudioActivity

data class EditingToolItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val action: () -> Unit
)

@Composable
fun EditingToolsScreen(
    viewModel: BrowserViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val tools = listOf(
        EditingToolItem(
            title = "Video Editing",
            icon = Icons.Default.MovieCreation,
            color = Color(0xFF818CF8),
            action = {
                // For now, placeholder or direct to video activity
                val intent = Intent(context, com.swift.browser.videoengine.ui.VideoPlayerActivity::class.java)
                context.startActivity(intent)
            }
        ),
        EditingToolItem(
            title = "Audio Editing",
            icon = Icons.Default.LibraryMusic,
            color = Color(0xFFF43F5E),
            action = {
                val intent = Intent(context, AudioPlayerActivity::class.java)
                context.startActivity(intent)
            }
        ),
        EditingToolItem(
            title = "Archive / ZIP Extractor",
            icon = Icons.Default.FolderZip,
            color = Color(0xFFF59E0B),
            action = {
                // Placeholder
            }
        ),
        EditingToolItem(
            title = "Web Studio",
            icon = Icons.Default.Code,
            color = Color(0xFF10B981),
            action = {
                val intent = Intent().apply {
                    setClassName(context, "com.swift.browser.webstudio.WebStudioActivity")
                }
                context.startActivity(intent)
            }
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "EDITING TOOLS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tools) { tool ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { tool.action() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = tool.title,
                                tint = tool.color,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = tool.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
