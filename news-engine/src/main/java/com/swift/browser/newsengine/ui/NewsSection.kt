package com.swift.browser.newsengine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.newsengine.api.NewsEngineApi
import com.swift.browser.newsengine.state.NewsUiState

@Composable
fun NewsEngineUi(
    engine: NewsEngineApi,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by engine.uiState.collectAsState()
    NewsSection(
        state = state,
        onCategorySelected = { engine.selectCategory(it) },
        onArticleClick = onArticleClick,
        modifier = modifier
    )
}

@Composable
fun NewsSection(
    state: NewsUiState,
    onCategorySelected: (String) -> Unit,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    Column(modifier = modifier.fillMaxWidth()) {
        // Visual Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📰", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "News Section",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Categories Row
        NewsCategoryRow(
            selectedCategory = state.feedCategory,
            onCategorySelected = onCategorySelected
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Content Area: Loading / Articles
        if (state.isFeedLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else if (state.articles.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (article in state.articles) {
                    NewsArticleCard(
                        article = article,
                        onClick = onArticleClick
                    )
                }
            }
        }
    }
}
