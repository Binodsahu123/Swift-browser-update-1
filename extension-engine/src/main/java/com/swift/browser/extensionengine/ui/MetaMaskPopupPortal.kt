package com.swift.browser.extensionengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.extensionengine.ExtensionEngineApi

@Composable
fun MetaMaskPopupPortal(
    extensionId: String,
    popupUrl: String,
    api: ExtensionEngineApi,
    modifier: Modifier = Modifier
) {
    var accountAddress by remember { mutableStateOf("0x71C...39F2") }
    var ethBalance by remember { mutableStateOf("1.428 ETH") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(14.dp)
    ) {
        // MetaMask Network Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ethereum Mainnet", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Balance Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(accountAddress, fontSize = 12.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(4.dp))
                Text(ethBalance, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("≈ $3,742.10 USD", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons: Send, Swap, Faucet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {},
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {},
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Swap", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {},
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Opacity, contentDescription = "Faucet", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Faucet", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Webview embed fallback for real extension HTML
        GenericPopupView(extensionId = extensionId, popupUrl = popupUrl, api = api, modifier = Modifier.weight(1f))
    }
}

