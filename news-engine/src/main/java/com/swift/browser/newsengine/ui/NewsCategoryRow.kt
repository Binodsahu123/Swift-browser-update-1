package com.swift.browser.newsengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NEWS_CATEGORIES = listOf(
    "For You",
    "India",
    "Tech",
    "Sports",
    "Entertainment",
    "Business",
    "Health",
    "Science"
)

@Composable
fun NewsCategoryRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    categories: List<String> = NEWS_CATEGORIES,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Surface(
                modifier = Modifier.clickable { onCategorySelected(category) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Color(0xFF3B82F6) else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                contentColor = if (isSelected) Color.White else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569))
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
