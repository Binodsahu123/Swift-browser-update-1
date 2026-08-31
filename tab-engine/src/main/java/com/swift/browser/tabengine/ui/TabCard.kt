package com.swift.browser.tabengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swift.browser.tabengine.model.TabModel

@Composable
fun TabCard(
    tab: TabModel,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .padding(4.dp)
            .width(140.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tab.title,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor, modifier = Modifier.size(16.dp))
        }
    }
}
