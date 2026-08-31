package com.swift.browser.bookmarkengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.bookmarkengine.Bookmark

@Composable
fun BookmarksOverlay(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    isGlass: Boolean = false
) {
    Surface(
        color = if (isGlass) Color(0xFF0B1220) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Saved Bookmarks", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No saved bookmarks yet.",
                        color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks) { bm ->
                        BookmarkItemRow(
                            bookmark = bm,
                            onClick = { onNavigate(bm.url) },
                            onDelete = { onDelete(bm) },
                            isGlass = isGlass
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkItemRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isGlass: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isGlass) Color.White else Color.Unspecified
                )
                Text(
                    text = bookmark.url,
                    fontSize = 11.sp,
                    color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Bookmark",
                    tint = if (isGlass) Color.White.copy(alpha = 0.8f) else LocalContentColor.current
                )
            }
        }
    }
}
