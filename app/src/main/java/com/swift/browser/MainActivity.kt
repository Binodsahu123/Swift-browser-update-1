package com.swift.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.swift.browser.browserengine.splash.FirstLaunchManager
import com.swift.browser.browserengine.splash.SplashScreenEngine
import com.swift.browser.browserengine.splash.SplashRoute
import com.swift.browser.browserengine.splash.OnboardingFlow
import com.swift.browser.browserengine.splash.SplashFlowContainer
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.swift.browser.browserengine.ui.BrowserScreen
import com.swift.browser.browserengine.BrowserViewModel
import com.swift.browser.browserengine.BrowserViewModelFactory
import com.swift.browser.data.BrowserDatabase
import com.swift.browser.data.BrowserRepository
import com.swift.browser.databasecore.PreferenceManager
import com.swift.browser.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: BrowserViewModel

    private var pendingPermissionRequestId: String? = null
    private var pendingPermissionCallback: ((com.swift.browser.permissionengine.AndroidPermissionResult) -> Unit)? = null

    private val runtimePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        val anyDenied = result.values.any { !it }
        val androidResult = com.swift.browser.permissionengine.AndroidPermissionResult(
            granted = allGranted,
            denied = anyDenied,
            permanentlyDenied = false,
            individuallyGrantedPermissions = result
        )
        // Canonical single path: invoke the registered callback, which dispatches to PermissionEngine.resumeTransaction(requestId, androidResult)
        pendingPermissionCallback?.invoke(androidResult)
        pendingPermissionCallback = null
        pendingPermissionRequestId = null
    }

    private val systemPermissionRequester = com.swift.browser.permissionengine.AndroidRuntimePermissionManager.SystemPermissionRequester { requestId, permissions, callback ->
        if (permissions.isNotEmpty()) {
            pendingPermissionRequestId = requestId
            pendingPermissionCallback = callback
            runtimePermissionLauncher.launch(permissions.toTypedArray())
        } else {
            callback(
                com.swift.browser.permissionengine.AndroidPermissionResult(
                    granted = true,
                    denied = false,
                    permanentlyDenied = false,
                    individuallyGrantedPermissions = emptyMap()
                )
            )
        }
    }

    private val voicePermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
        android.util.Log.i("MainActivity", "Permissions result. RECORD_AUDIO: $recordAudioGranted, CAMERA: $cameraGranted")
    }

    private var pendingMediaProjectionCallback: ((Int, Intent?) -> Unit)? = null
    private var pendingMediaProjectionRequestId: String? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data
        android.util.Log.i("MainActivity", "MediaProjection activity result: resultCode=$resultCode, data=$data")
        pendingMediaProjectionCallback?.invoke(resultCode, data)
        pendingMediaProjectionCallback = null
        pendingMediaProjectionRequestId = null
    }

    private val mediaProjectionHostRequester = object : com.swift.browser.browserengine.screencapture.ScreenCaptureManager.MediaProjectionHostRequester {
        override fun requestMediaProjectionConsent(requestId: String, onResult: (resultCode: Int, resultData: Intent?) -> Unit) {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
            if (mediaProjectionManager != null) {
                pendingMediaProjectionRequestId = requestId
                pendingMediaProjectionCallback = onResult
                val intent = mediaProjectionManager.createScreenCaptureIntent()
                mediaProjectionLauncher.launch(intent)
            } else {
                onResult(android.app.Activity.RESULT_CANCELED, null)
            }
        }

        override fun startForegroundService(resultCode: Int, resultData: Intent) {
            com.swift.browser.screencapture.ScreenCaptureForegroundService.startService(
                this@MainActivity,
                resultCode,
                resultData
            )
        }

        override fun stopForegroundService() {
            com.swift.browser.screencapture.ScreenCaptureForegroundService.stopService(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "MAIN_ACTIVITY_START",
            className = "MainActivity",
            methodName = "onCreate",
            success = true
        )
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.swift.browser.permissionengine.AndroidRuntimePermissionManager.registerSystemRequester(systemPermissionRequester)
        com.swift.browser.browserengine.screencapture.ScreenCaptureManager.registerHostRequester(mediaProjectionHostRequester)

        splashScreen.setKeepOnScreenCondition { com.swift.browser.browserengine.StartupCoordinator.instance.isBlockingSplash }

        val firstLaunchManager = FirstLaunchManager(this)
        val splashScreenEngine = SplashScreenEngine(this, firstLaunchManager)
        val initialRoute = SplashRoute.SplashOnly
        val pendingLaunchIntent = intent

        var prefsLocal: PreferenceManager? = null

        try {
            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "DATABASE_CREATE",
                className = "BrowserDatabase",
                methodName = "getDatabase",
                success = true
            )
            val db = BrowserDatabase.getDatabase(applicationContext)

            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "REPOSITORY_CREATE",
                className = "BrowserRepository",
                methodName = "init",
                success = true
            )
            val repository = BrowserRepository(db)

            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "PREFS_CREATE",
                className = "PreferenceManager",
                methodName = "init",
                success = true
            )
            val prefs = PreferenceManager(applicationContext)
            prefsLocal = prefs

            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "VIEWMODEL_FACTORY_CREATE",
                className = "BrowserViewModelFactory",
                methodName = "init",
                success = true
            )
            val factory = BrowserViewModelFactory(application, repository, prefs)

            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "VIEWMODEL_CREATE",
                className = "MainActivity",
                methodName = "ViewModelProvider.get",
                success = true
            )
            viewModel = ViewModelProvider(this, factory)[BrowserViewModel::class.java]
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error during startup initialization, using fallback ViewModel", t)
            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "MAIN_ACTIVITY_START",
                className = "MainActivity",
                methodName = "onCreate.fallback",
                success = false,
                error = t
            )
            if (!::viewModel.isInitialized) {
                viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
            }
        }

        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "SET_CONTENT",
            className = "MainActivity",
            methodName = "setContent",
            success = true
        )

        setContent {
            val appThemeMode = prefsLocal?.appTheme?.collectAsStateWithLifecycle()?.value ?: "System"
            var currentRoute by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialRoute) }
            
            androidx.compose.runtime.LaunchedEffect(Unit) {
                com.swift.browser.browserengine.StartupCoordinator.instance.onFirstFrameRendered()
                if (pendingLaunchIntent != null && ::viewModel.isInitialized) {
                    viewModel.handleIncomingIntent(pendingLaunchIntent)
                }
                com.swift.browser.analyticscore.StartupTracker.recordStage(
                    stage = "COMPOSE_READY",
                    className = "MainActivity",
                    methodName = "LaunchedEffect",
                    success = true
                )
                com.swift.browser.analyticscore.StartupTracker.recordStage(
                    stage = "STARTUP_COMPLETE",
                    className = "MainActivity",
                    methodName = "onCreate.completed",
                    success = true
                )
            }
            
            MyApplicationTheme(appThemeMode = appThemeMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (currentRoute != SplashRoute.Home) {
                        SplashFlowContainer(
                            engine = splashScreenEngine,
                            firstLaunchManager = firstLaunchManager,
                            onComplete = {
                                currentRoute = SplashRoute.Home
                            }
                        )
                    } else {
                        BrowserScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::viewModel.isInitialized) {
            viewModel.handleIncomingIntent(intent)
        }
    }

    override fun onPause() {
        com.swift.browser.browserengine.BrowserEngine.lifecycleEngine.onPause()
        if (::viewModel.isInitialized) {
            viewModel.onAppPause()
            viewModel.captureActiveVideoState()
            viewModel.saveTabsState()
            val tabId = viewModel.uiState.value.activeTabId
            if (tabId.isNotEmpty()) {
                com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.onWebViewPaused(tabId)
            }
        }
        super.onPause()
    }

    override fun onStop() {
        if (::viewModel.isInitialized) {
            viewModel.saveTabsState()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        com.swift.browser.browserengine.BrowserEngine.lifecycleEngine.onResume()
        if (::viewModel.isInitialized) {
            val tabId = viewModel.uiState.value.activeTabId
            if (tabId.isNotEmpty()) {
                com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.onWebViewResumed(tabId)
            }
        }
        
        if (::viewModel.isInitialized) {
            viewModel.onAppResume()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::viewModel.isInitialized) {
            // viewModel.clearWebViewCache(applicationContext)
        }
    }

    override fun onDestroy() {
        if (::viewModel.isInitialized) {
            viewModel.onAppDestroy()
        }
        com.swift.browser.permissionengine.AndroidRuntimePermissionManager.unregisterSystemRequester(systemPermissionRequester)
        com.swift.browser.browserengine.screencapture.ScreenCaptureManager.unregisterHostRequester(mediaProjectionHostRequester)
        com.swift.browser.browserengine.screencapture.ScreenCaptureManager.onActivityDestroyed()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE && ::viewModel.isInitialized) {
            // viewModel.clearWebViewCache(applicationContext)
        }
    }
}
