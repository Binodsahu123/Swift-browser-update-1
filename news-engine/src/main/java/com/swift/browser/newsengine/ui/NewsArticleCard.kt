package com.swift.browser.newsengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swift.browser.newsengine.NewsItemEntity

@Composable
fun NewsArticleCard(
    article: NewsItemEntity,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    Card(
        onClick = { onClick(article.sourceUrl) },
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color.White),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Image section (Left)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                if (article.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                    )
                }
            }

            // Content section (Right)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    text = article.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${article.sourceName} • ${article.publishedAt}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
