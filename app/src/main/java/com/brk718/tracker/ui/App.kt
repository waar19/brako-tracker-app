package com.brk718.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brk718.tracker.ui.add.AddScreen
import com.brk718.tracker.ui.auth.AmazonAuthScreen
import com.brk718.tracker.ui.detail.DetailScreen
import com.brk718.tracker.ui.gmail.GmailScreen
import com.brk718.tracker.ui.home.HomeScreen
import com.brk718.tracker.ui.settings.ArchivedScreen
import com.brk718.tracker.ui.settings.SettingsScreen
import com.brk718.tracker.ui.settings.SettingsViewModel
import com.brk718.tracker.ui.theme.TrackerTheme

object Routes {
    const val LIST     = "list"
    const val ADD      = "add"
    const val DETAIL   = "detail/{shipmentId}"
    const val GMAIL    = "gmail"
    const val AMAZON_AUTH = "amazon_auth"
    const val SETTINGS = "settings"
    const val ARCHIVED = "archived"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun App() {
    val navController = rememberNavController()

    // El SettingsViewModel vive a nivel de App para que el tema sea reactivo
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val darkTheme = when (settingsState.preferences.theme) {
        "light"  -> false
        "dark"   -> true
        else     -> isSystemInDarkTheme()
    }

    TrackerTheme(darkTheme = darkTheme) {
        NavHost(navController = navController, startDestination = Routes.LIST) {
            composable(Routes.LIST) {
                HomeScreen(
                    onAddClick        = { navController.navigate(Routes.ADD) },
                    onShipmentClick   = { id -> navController.navigate(Routes.detail(id)) },
                    onGmailClick      = { navController.navigate(Routes.GMAIL) },
                    onAmazonAuthClick = { navController.navigate(Routes.AMAZON_AUTH) },
                    onSettingsClick   = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.ADD) {
                AddScreen(
                    onBack    = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }
            composable(Routes.GMAIL) {
                GmailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.AMAZON_AUTH) {
                AmazonAuthScreen(
                    onBack         = { navController.popBackStack() },
                    onLoginSuccess = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack            = { navController.popBackStack() },
                    onGmailClick      = { navController.navigate(Routes.GMAIL) },
                    onAmazonAuthClick = { navController.navigate(Routes.AMAZON_AUTH) },
                    onArchivedClick   = { navController.navigate(Routes.ARCHIVED) }
                )
            }
            composable(Routes.ARCHIVED) {
                ArchivedScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route     = Routes.DETAIL,
                arguments = listOf(navArgument("shipmentId") { type = NavType.StringType })
            ) {
                DetailScreen(
                    onBack            = { navController.popBackStack() },
                    onAmazonAuthClick = { navController.navigate(Routes.AMAZON_AUTH) }
                )
            }
        }
    }
}
