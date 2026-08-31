package com.swift.browser.tabengine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import com.swift.browser.tabengine.model.TabGroupModel

@Composable
fun TabStrip(
    activeGroup: TabGroupModel?,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit
) {
    if (activeGroup == null) return
    LazyRow(modifier = Modifier.fillMaxWidth().height(64.dp)) {
        items(activeGroup.tabs) { tab ->
            TabCard(
                tab = tab,
                isActive = tab.id == activeTabId,
                onClick = { onTabSelected(tab.id) },
                onClose = { onTabClosed(tab.id) }
            )
        }
        item {
            androidx.compose.material3.IconButton(onClick = onNewTab) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = "New Tab"
                )
            }
        }
    }
}
