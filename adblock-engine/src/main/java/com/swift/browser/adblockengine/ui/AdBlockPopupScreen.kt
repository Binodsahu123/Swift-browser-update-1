package com.swift.browser.adblockengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.swift.browser.adblockengine.AdProtectionEngineApi

@Composable
fun AdBlockPopupScreen(
    adProtectionApi: AdProtectionEngineApi,
    modifier: Modifier = Modifier
) {
    val uiState by adProtectionApi.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.04f)
            ),
            border = BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.08f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Total Blocked Items",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${uiState.blockedAdsCount}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Active Page Filter",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (uiState.currentSiteWhitelisted) "Allowed" else "Secure",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.currentSiteWhitelisted) Color(0xFFEAB308) else Color(0xFF3B82F6)
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "RULE CONTEXTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f)
            )

            // =========================================================
            // GLOBAL AD BLOCKING
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.03f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(
                            "Block Advertisements",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Remove popups, overlays & banners",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }

                Switch(
                    checked = uiState.globalAdBlockEnabled,
                    onCheckedChange = {
                        adProtectionApi.setGlobalAdBlockEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF6366F1)
                    )
                )
            }

            // =========================================================
            // TRACKER BLOCKING
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.03f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(
                            "Block Tracking Scripts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Shield cookies and host analytics",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }

                Switch(
                    checked = uiState.globalTrackersEnabled,
                    onCheckedChange = {
                        adProtectionApi.setGlobalTrackersEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF6366F1)
                    )
                )
            }

            // =========================================================
        }
    }
}
