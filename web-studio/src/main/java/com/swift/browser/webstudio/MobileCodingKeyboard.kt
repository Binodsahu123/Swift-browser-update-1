package com.swift.browser.webstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MobileCodingKeyboard(onKeyPress: (String) -> Unit) {
    val keys = listOf("{", "}", "[", "]", "(", ")", "<", ">", "/", "=", ";", ":", "\"", "'", "Tab")
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF1E293B)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { Spacer(modifier = Modifier.width(4.dp)) }
        items(keys) { key ->
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .background(Color(0xFF334155), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable { onKeyPress(key) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = key, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item { Spacer(modifier = Modifier.width(4.dp)) }
    }
}
