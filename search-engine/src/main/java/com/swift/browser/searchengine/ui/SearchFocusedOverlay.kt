package com.swift.browser.searchengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.searchengine.SearchSuggestion
import com.swift.browser.searchengine.SuggestionType

data class SearchTabPreview(
    val title: String,
    val url: String
)

data class SearchHistoryPreview(
    val title: String,
    val url: String
)

@Composable
fun SearchFocusedOverlay(
    activeTab: SearchTabPreview?,
    searchSuggestions: List<SearchSuggestion>,
    currentInputUrl: String,
    addressBarPosition: String,
    history: List<SearchHistoryPreview>,
    onSearch: (String) -> Unit,
    onEdit: (String) -> Unit,
    isGlass: Boolean = false,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = if (isGlass) Color(0xF20A0E17) else MaterialTheme.colorScheme.background
            )
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (addressBarPosition == "bottom") Modifier.statusBarsPadding() else Modifier)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (addressBarPosition == "bottom") Arrangement.Bottom else Arrangement.Top
        ) {
            // First: Web Page Edit Card at the absolute top of the focused search screen
            if (activeTab != null && activeTab.url != "swift://newtab" && activeTab.url != "swift://newtab-incognito") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGlass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (isGlass) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTab.title.ifEmpty { "Active Webpage" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeTab.url,
                                fontSize = 12.sp,
                                color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, activeTab.url)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(activeTab.url))
                                android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = {
                                onEdit(activeTab.url)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Second: Search suggestions list under the Web Page edit card
            if (searchSuggestions.isNotEmpty() || currentInputUrl.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGlass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        if (searchSuggestions.isNotEmpty()) {
                            searchSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (suggestion.type == SuggestionType.HISTORY || suggestion.type == SuggestionType.BOOKMARK) {
                                                onSearch(suggestion.url)
                                            } else {
                                                onSearch(suggestion.title)
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (suggestion.type) {
                                        SuggestionType.SEARCH -> Icons.Default.Search
                                        SuggestionType.HISTORY -> Icons.Default.History
                                        SuggestionType.BOOKMARK -> Icons.Default.Star
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = suggestion.type.name,
                                        tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (suggestion.type == SuggestionType.HISTORY || suggestion.type == SuggestionType.BOOKMARK) {
                                            Text(
                                                text = suggestion.url,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    if (suggestion.type == SuggestionType.SEARCH) {
                                        IconButton(
                                            onClick = { onEdit(suggestion.title) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Refine search",
                                                tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 135f)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Show recent history if search is empty
                            if (history.isNotEmpty()) {
                                history.take(8).forEach { historyItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSearch(historyItem.url) }
                                            .padding(vertical = 10.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "History",
                                            tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = historyItem.title.ifBlank { historyItem.url },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = historyItem.url,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onEdit(historyItem.url) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Refine",
                                                tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 135f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Trending Searches
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Trending Searches",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            val trending = listOf("ChatGPT", "Local Weather", "Breaking News", "Amazon", "Android 15")
                            trending.forEach { trend ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSearch(trend) }
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Trending",
                                        tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = trend,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onEdit(trend) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Refine",
                                            tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 135f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            bottomBar()
        }
    }
}
