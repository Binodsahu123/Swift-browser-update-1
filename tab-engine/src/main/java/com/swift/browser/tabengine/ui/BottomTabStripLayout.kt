@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.swift.browser.tabengine.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.tabengine.model.TabModel

@Composable
fun BottomTabStripLayout(
    tabs: List<TabModel>,
    activeTabId: String?,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onVoiceClick: () -> Unit,
    isGlass: Boolean = false,
    applyNavigationPadding: Boolean = true
) {
    Surface(
        color = if (isGlass) Color(0xD90A0E17) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
        border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        val navModifier = if (applyNavigationPadding) Modifier.navigationBarsPadding() else Modifier
        Row(
            modifier = navModifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Horizontal scrolling tab items
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentGroupId = tabs.find { it.id == activeTabId }?.groupId
                val groupTabs = if (currentGroupId != null) tabs.filter { it.groupId == currentGroupId } else emptyList()
                items(groupTabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    val sizeDp by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isActive) 32.dp else 26.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                        ),
                        label = "bottom_strip_tab_size"
                    )

                    Box(
                        modifier = Modifier
                            .animateItemPlacement(
                                animationSpec = androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                                )
                            )
                            .size(38.dp)
                    ) {
                        // Circular tab favicon container
                        Surface(
                            onClick = { onTabSelect(tab.id) },
                            shape = CircleShape,
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(sizeDp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val favicon = tab.favicon
                                if (favicon != null) {
                                    Image(
                                        bitmap = favicon.asImageBitmap(),
                                        contentDescription = tab.title,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    val letter = remember(tab.title, tab.url) {
                                        val display = if (tab.url.startsWith("swift://newtab")) {
                                            if (tab.isIncognito) "I" else "N"
                                        } else {
                                            val host = try { Uri.parse(tab.url).host } catch (e: Exception) { null }
                                            if (!host.isNullOrEmpty()) {
                                                host.removePrefix("www.").firstOrNull()?.toString()
                                            } else {
                                                tab.title.firstOrNull()?.toString()
                                            }
                                        } ?: "O"
                                        display.uppercase()
                                    }
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Close button "X" on active tab
                        if (isActive && tabs.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(12.dp)
                                    .clickable { onTabClose(tab.id) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Swift Assistant Voice command action
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("swift_voice_bottom_nav_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Swift Assistant",
                    tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Middle: Plus sign to add new tab
            if (tabs.size > 1) {
                IconButton(
                    onClick = onNewTab,
                    modifier = Modifier
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Right: Caret Arrow icon to toggle/show tab switcher
            if (tabs.size > 1) {
                IconButton(
                    onClick = onOpenTabSwitcher,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Tab Switcher",
                        tint = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
