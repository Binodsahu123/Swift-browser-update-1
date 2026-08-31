@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.swift.browser.tabengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherLayout(
    groups: List<TabGroupModel>,
    activeGroupId: String?,
    activeTabId: String?,
    onGroupSelected: (String) -> Unit,
    onNewGroup: () -> Unit,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
    isPrivateUnlocked: Boolean = true,
    onAuthenticateBiometric: (() -> Unit)? = null,
    onCloseAllPrivateTabs: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: Normal, 1: Incognito, 2: Groups
    var isGridView by remember { mutableStateOf(true) }

    val allTabs = remember(groups) { groups.flatMap { it.tabs } }
    val normalTabs = remember(groups) { groups.filter { !it.isPrivate && !it.isIncognito && it.privateSessionId == null }.flatMap { it.tabs }.filter { !it.isPrivate && !it.isIncognito } }
    val privateTabs = remember(groups) { groups.filter { it.isPrivate || it.isIncognito || it.privateSessionId != null }.flatMap { it.tabs }.filter { it.isPrivate || it.isIncognito } }

    val displayedTabs = remember(selectedFilterIndex, normalTabs, privateTabs, allTabs, searchQuery) {
        val base = when (selectedFilterIndex) {
            1 -> privateTabs
            2 -> allTabs
            else -> normalTabs
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        ) {
            // 1. Top Bar with Search & View Action Icons (Matching Video 00:46)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onCloseSwitcher,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Tab Switcher", tint = Color.White)
                }

                // Search Bar in Tab Switcher
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search tabs...",
                                        color = Color(0xFF64748B),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Action Icons (Grid/List toggle, New Group, Edit)
                IconButton(
                    onClick = { isGridView = !isGridView },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.GridView else Icons.Default.ViewAgenda,
                        contentDescription = "Toggle View",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNewGroup,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Workspaces, contentDescription = "New Tab Group", tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = { /* Batch edit */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Tabs", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // 2. Segmented Filter Tabs: Normal, Incognito, Groups
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = listOf(
                    "Normal (${normalTabs.size})",
                    "Private (${privateTabs.size})",
                    "Groups (${groups.size})"
                )

                chips.forEachIndexed { index, label ->
                    val isSelected = selectedFilterIndex == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .clickable { selectedFilterIndex = index },
                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFF334155))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Tab Grid View or Biometric Lock Guard
            if (selectedFilterIndex == 1 && !isPrivateUnlocked && privateTabs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1B4B))
                                .border(BorderStroke(1.5.dp, Color(0xFF6366F1)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Lock",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Private Tabs Locked",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Unlock with fingerprint or face to view ${privateTabs.size} active private ${if (privateTabs.size == 1) "tab" else "tabs"}.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { onAuthenticateBiometric?.invoke() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unlock with Biometrics",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (onCloseAllPrivateTabs != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = { onCloseAllPrivateTabs.invoke() }
                            ) {
                                Text(
                                    text = "Close All Private Tabs",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            } else if (displayedTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No tabs match \"$searchQuery\"" else "No open tabs in this section",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isGridView) 2 else 1),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(displayedTabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        var isPressed by remember { mutableStateOf(false) }
                        val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = com.swift.browser.tabengine.animation.TabAnimationTransitions.FluidSpring,
                            label = "tab_card_scale"
                        )

                        Card(
                            modifier = Modifier
                                .animateItemPlacement(
                                    animationSpec = androidx.compose.animation.core.spring(
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                                    )
                                )
                                .fillMaxWidth()
                                .height(if (isGridView) 190.dp else 110.dp)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                }
                                .clickable {
                                    isPressed = true
                                    if ((tab.isPrivate || tab.isIncognito) && !isPrivateUnlocked) {
                                        onAuthenticateBiometric?.invoke()
                                    } else {
                                        onTabSelected(tab.id)
                                        onCloseSwitcher()
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) Color(0xFF3B82F6) else Color(0xFF334155)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Tab Card Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Public,
                                                contentDescription = null,
                                                tint = Color(0xFF60A5FA),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tab.title.ifBlank { "New Tab" },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onTabClosed(tab.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close tab",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Tab Thumbnail Canvas / Body
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color(0xFF0B1120)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = tab.title.ifBlank { "Swift Browser" },
                                            color = Color(0xFF64748B),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // URL Footer
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tab.url.replace("https://", "").replace("http://", ""),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Bottom Right Floating Buttons (Matching Video: Download FAB + New Tab FAB)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Salmon / Orange Download Floating Action Button
            FloatingActionButton(
                onClick = { /* Quick Download Manager view */ },
                containerColor = Color(0xFFFF7A59),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Downloads",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Primary Add Tab (+) Floating Action Button
            FloatingActionButton(
                onClick = {
                    onNewTab(selectedFilterIndex == 1)
                    onCloseSwitcher()
                },
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
