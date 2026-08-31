package com.swift.browser.tabengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.swift.browser.tabengine.model.TabGroupModel

@Composable
fun GroupCard(
    group: TabGroupModel,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${group.name} (${group.tabs.size})",
            color = textColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
