package com.swift.browser.settingsengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder

open class AdvancedSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsEngine = SettingsEngineImpl(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdvancedSettingsApp(settingsEngine, onFinish = { finish() })
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsApp(settingsEngine: SettingsEngine, onFinish: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main_settings") {
        composable("main_settings") {
            SettingsMainScreen(
                settingsEngine = settingsEngine,
                onBack = onFinish,
                onNavigateToEngines = {
                    navController.navigate("engines_list")
                },
                onNavigateToPasswordManager = {
                    navController.navigate("password_manager")
                },
                onNavigateToLiveStreamSettings = {
                    navController.navigate("live_stream_settings")
                }
            )
        }

        composable("live_stream_settings") {
            LiveStreamSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("password_manager") {
            com.swift.browser.passwordengine.ui.PasswordManagerScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("engines_list") {
            AdvancedEngineSettingsScreen(
                settingsEngine = settingsEngine,
                onBack = { navController.popBackStack() },
                onNavigateToEngine = { id, name ->
                    val encodedName = URLEncoder.encode(name, "UTF-8")
                    navController.navigate("engine_detail/$id/$encodedName")
                }
            )
        }

        composable(
            route = "engine_detail/{engineId}/{engineName}",
            arguments = listOf(
                navArgument("engineId") { type = NavType.StringType },
                navArgument("engineName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val engineId = backStackEntry.arguments?.getString("engineId") ?: ""
            val engineNameEncoded = backStackEntry.arguments?.getString("engineName") ?: ""
            val engineName = try { URLDecoder.decode(engineNameEncoded, "UTF-8") } catch (e: Exception) { engineNameEncoded }

            EngineDetailSettingsScreen(
                settingsEngine = settingsEngine,
                engineId = engineId,
                engineName = engineName,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
