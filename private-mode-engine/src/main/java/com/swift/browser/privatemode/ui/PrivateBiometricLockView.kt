package com.swift.browser.privatemode.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.swift.browser.privatemode.BiometricAuthResult
import com.swift.browser.privatemode.PrivateBiometricConfig
import com.swift.browser.privatemode.PrivateModeEngineApi
import com.swift.browser.privatemode.PrivateModeEngineProvider

/**
 * Finds the nearest FragmentActivity in the Context hierarchy.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Screen displayed when active private tabs are locked behind BiometricPrompt authentication.
 * Prompts user for fingerprint or face unlock before revealing private tabs.
 */
@Composable
fun PrivateTabsBiometricLockView(
    modifier: Modifier = Modifier,
    engine: PrivateModeEngineApi = PrivateModeEngineProvider.getEngine(LocalContext.current),
    onAuthenticated: () -> Unit = {},
    onCloseAllPrivateTabs: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var authErrorText by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "halo_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    fun triggerBiometricUnlock() {
        val activity = context.findFragmentActivity()
        if (activity != null) {
            isAuthenticating = true
            authErrorText = null
            engine.authenticateBiometric(
                activity = activity,
                config = PrivateBiometricConfig(
                    promptTitle = "Unlock Private Tabs",
                    promptSubtitle = "Fingerprint or face unlock required",
                    promptDescription = "Authenticate to view active private tabs and sessions",
                    allowDeviceCredentialFallback = true
                )
            ) { result ->
                isAuthenticating = false
                when (result) {
                    is BiometricAuthResult.Success -> {
                        authErrorText = null
                        onAuthenticated()
                    }
                    is BiometricAuthResult.Error -> {
                        authErrorText = result.message
                    }
                    is BiometricAuthResult.Failed -> {
                        authErrorText = "Biometric not recognized. Please try again."
                    }
                    is BiometricAuthResult.Cancelled -> {
                        // User cancelled, keep locked
                    }
                    is BiometricAuthResult.Unavailable -> {
                        // If hardware is not configured, grant access with caution or provide PIN fallback
                        authErrorText = "Biometrics unavailable on this device."
                    }
                }
            }
        } else {
            // Direct unlock if no FragmentActivity attached
            engine.unlockPrivateTabs()
            onAuthenticated()
        }
    }

    // Auto-trigger biometric prompt when the lock screen appears
    LaunchedEffect(Unit) {
        triggerBiometricUnlock()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Biometric Shield Visual with Halo
            Box(
                modifier = Modifier
                    .size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Halo
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF6366F1).copy(alpha = 0.35f),
                                    Color(0xFF818CF8).copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Inner Glow Background
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1B4B))
                        .border(1.5.dp, Color(0xFF6366F1), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Lock",
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Private Tabs Locked",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Unlock with fingerprint, face, or device PIN to view your active private tabs.",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            if (authErrorText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = authErrorText.orEmpty(),
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Unlock Button
            Button(
                onClick = { triggerBiometricUnlock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock with Biometrics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (onCloseAllPrivateTabs != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onCloseAllPrivateTabs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Close All Private Tabs",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

/**
 * Guard Composable that wraps private tab content.
 * If the current tab is private and biometric lock is enabled and locked,
 * shows the Biometric Lock View instead of tab contents.
 */
@Composable
fun PrivateTabBiometricGuard(
    isPrivate: Boolean,
    isUnlocked: Boolean,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseAllPrivateTabs: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (isPrivate && !isUnlocked) {
        PrivateTabsBiometricLockView(
            modifier = modifier,
            onAuthenticated = onAuthenticated,
            onCloseAllPrivateTabs = onCloseAllPrivateTabs
        )
    } else {
        content()
    }
}
