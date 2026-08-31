package com.swift.browser.browserengine.splash

import com.swift.browser.browserengine.StartupCoordinator
import com.swift.browser.browserengine.StartupState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RocketLaunch
import kotlinx.coroutines.delay




@Composable
fun SplashFlowContainer(
    engine: SplashScreenEngine,
    firstLaunchManager: FirstLaunchManager,
    onComplete: () -> Unit
) {
    var currentRoute by remember { mutableStateOf<SplashRoute>(SplashRoute.SplashOnly) }
    val startupState by StartupCoordinator.instance.startupState.collectAsStateWithLifecycle()
    val startupError by StartupCoordinator.instance.startupError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try {
            val nextRoute = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                try {
                    engine.decideNextRoute()
                } catch (t: Throwable) {
                    android.util.Log.e("SplashFlowContainer", "Error in decideNextRoute", t)
                    SplashRoute.Home
                }
            } ?: SplashRoute.Home

            if (nextRoute == SplashRoute.Onboarding) {
                currentRoute = SplashRoute.Onboarding
            } else {
                onComplete()
            }
        } catch (t: Throwable) {
            android.util.Log.e("SplashFlowContainer", "Splash watchdog fallback triggered", t)
            onComplete()
        }
    }

    if (startupState == StartupState.FAILED) {
        BrowserRecoveryScreen(
            error = startupError,
            onRetry = {
                StartupCoordinator.instance.retryStartup()
            },
            onContinue = {
                onComplete()
            }
        )
    } else {
        Crossfade(targetState = currentRoute, label = "splash_crossfade") { route ->
            when (route) {
                SplashRoute.SplashOnly -> SplashScreen()
                SplashRoute.Onboarding -> OnboardingFlow(engine, firstLaunchManager, onComplete)
                else -> {
                    LaunchedEffect(Unit) {
                        onComplete()
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserRecoveryScreen(
    error: Throwable?,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Startup Recovery",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error?.localizedMessage ?: "An issue occurred during app initialization.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Retry Startup", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Continue to Browser", color = Color.White)
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Logo",
                tint = Color(0xFF38BDF8),
                modifier = Modifier
                    .size(120.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Swift Browser",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OnboardingFlow(engine: SplashScreenEngine, firstLaunchManager: FirstLaunchManager, onComplete: () -> Unit = {}) {
    var step by remember { mutableIntStateOf(1) }
    val context = LocalContext.current

    val nextStep: () -> Unit = {
        when (step) {
            3 -> firstLaunchManager.markAiScreenCompleted()
            4 -> firstLaunchManager.markNotificationCompleted()
            5 -> firstLaunchManager.markFilePermissionCompleted()
            7 -> firstLaunchManager.markPrivacyAccepted()
        }

        if (step < 7) {
            step++
        } else {
            firstLaunchManager.completeOnboarding()
            onComplete()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        nextStep()
    }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        val granted = map.values.any { it }
        if (granted) {
            MediaPermissionEngine(context).markFilePermissionGranted()
        }
        nextStep()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (step > 1) {
                    TextButton(onClick = { step-- }) {
                        Text("Back", color = Color(0xFF94A3B8), fontSize = 16.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
                
                if (step < 7) {
                    TextButton(onClick = { 
                        firstLaunchManager.completeOnboarding()
                        onComplete()
                    }) {
                        Text("Skip All", color = Color(0xFF94A3B8), fontSize = 16.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Crossfade(targetState = step, label = "onboarding_step") { currentStep ->
                when (currentStep) {
                    1 -> OnboardingScreen1()
                    2 -> OnboardingScreen2()
                    3 -> OnboardingScreen3()
                    4 -> OnboardingScreen4()
                    5 -> OnboardingScreen5()
                    6 -> OnboardingScreen6(firstLaunchManager)
                    7 -> OnboardingScreen7()
                    
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val primaryButtonText = when (step) {
                    1 -> "Get Started"
                    3 -> "Next"
                    4 -> "Allow Notifications"
                    5 -> "Allow File Access"
                    7 -> "Start Browsing"
                    else -> "Continue"
                }

                Button(
                    onClick = {
                        if (step == 4) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                nextStep()
                            }
                        } else if (step == 5) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                storageLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    )
                                )
                            } else {
                                storageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                            }
                        } else {
                            nextStep()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text(
                        primaryButtonText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (step in listOf(3, 4, 5)) {
                    TextButton(
                        onClick = nextStep,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Skip", color = Color(0xFF94A3B8), fontSize = 16.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.height(56.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    for (i in 1..7) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (i == step) 10.dp else 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (i == step) Color(0xFF6366F1) else Color(0xFF334155))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen1() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.RocketLaunch,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(160.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Welcome to\nSwift Browser",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "A powerful browser built for speed, privacy and smart features.",
            color = Color(0xFF94A3B8),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingScreen2() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Browser Features", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        FeatureItem("🚀", "Fast & Lightweight", "Optimized engine for speed")
        Spacer(modifier = Modifier.height(16.dp))
        FeatureItem("🤖", "Built-in AI", "Smart assistant at your fingertips")
        Spacer(modifier = Modifier.height(16.dp))
        FeatureItem("🧩", "Extension Support", "Customize with powerful add-ons")
        Spacer(modifier = Modifier.height(16.dp))
        FeatureItem("⬇️", "Smart Downloads", "Fast and organized downloads")
    }
}

@Composable
fun FeatureItem(icon: String, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.size(48.dp).background(Color(0xFF1E293B), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = Color(0xFF94A3B8), fontSize = 14.sp)
        }
    }
}

@Composable
fun OnboardingScreen3() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", fontSize = 80.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("AI at Your Fingertips", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Built-in AI Assistant.\nChat • Translate • Summarize\nWriting • Search • Voice AI\nSmart Suggestions",
            color = Color(0xFF94A3B8),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun OnboardingScreen4() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp).background(Color(0xFF1E293B), RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔔", fontSize = 48.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Stay Updated", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Receive alerts for:\nDownload completed\nBackground tasks\nBrowser alerts\nExtension notifications",
            color = Color(0xFF94A3B8),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun OnboardingScreen5() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                Text("📁", fontSize = 48.sp, modifier = Modifier.padding(8.dp))
                Text("🎬", fontSize = 48.sp, modifier = Modifier.padding(8.dp))
                Text("🎵", fontSize = 48.sp, modifier = Modifier.padding(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("All-in-One File Manager", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Manage all videos, music, images,\ndocuments, downloads, and folders.\nOne place for everything.",
            color = Color(0xFF94A3B8),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun OnboardingScreen6(firstLaunchManager: FirstLaunchManager) {
    var selected by remember { mutableStateOf(firstLaunchManager.getSelectedTheme()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Choose Your\nExperience", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Select your favorite theme.", color = Color(0xFF94A3B8), fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))
        ThemeCard("Light", selected == "Light") {
            selected = "Light"
            firstLaunchManager.saveSelectedTheme("Light")
        }
        Spacer(modifier = Modifier.height(16.dp))
        ThemeCard("Dark", selected == "Dark") {
            selected = "Dark"
            firstLaunchManager.saveSelectedTheme("Dark")
        }
        Spacer(modifier = Modifier.height(16.dp))
        ThemeCard("System", selected == "System") {
            selected = "System"
            firstLaunchManager.saveSelectedTheme("System")
        }
    }
}

@Composable
fun ThemeCard(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6366F1)) else androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (isSelected) {
                Box(modifier = Modifier.size(24.dp).background(Color(0xFF6366F1), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen7() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("We Respect\nYour Privacy", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("We protect your data and keep it always safe.", color = Color(0xFF94A3B8), fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        PrivacyItem("No data selling")
        Spacer(modifier = Modifier.height(16.dp))
        PrivacyItem("Private browsing")
        Spacer(modifier = Modifier.height(16.dp))
        PrivacyItem("Secure by default")
    }
}

@Composable
fun PrivacyItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.size(32.dp).background(Color(0xFF1E293B), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text("🔒", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun OnboardingScreen8() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("You're All Set!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enjoy a faster, smarter & safer browsing experience.", color = Color(0xFF94A3B8), fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(160.dp)
        )
    }
}
