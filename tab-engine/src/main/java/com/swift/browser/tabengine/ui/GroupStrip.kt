package com.swift.browser.tabengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swift.browser.tabengine.model.TabGroupModel

@Composable
fun GroupStrip(
    groups: List<TabGroupModel>,
    activeGroupId: String?,
    onGroupSelected: (String) -> Unit,
    onNewGroup: () -> Unit
) {
    LazyRow(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        items(groups) { group ->
            GroupCard(
                group = group,
                isActive = group.id == activeGroupId,
                onClick = { onGroupSelected(group.id) }
            )
        }
        item {
            IconButton(onClick = onNewGroup) {
                Icon(Icons.Default.Add, contentDescription = "New Group")
            }
        }
    }
}
