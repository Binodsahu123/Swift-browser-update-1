package com.swift.browser.browserengine.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.browserengine.LocalMediaItem
import com.swift.browser.browserengine.MediaType

@Composable
fun TabBarComponent(
    tabs: List<String>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(tabs) { tab ->
            val isSelected = tab == selectedTab
            Surface(
                modifier = Modifier.clickable { onTabSelected(tab) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                contentColor = if (isSelected) Color.White else Color.Gray
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(text = tab, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

object MediaScanner {
    fun scanMedia(context: Context, type: MediaType): List<LocalMediaItem> {
        return emptyList()
    }
}
