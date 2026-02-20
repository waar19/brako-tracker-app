package com.brk718.tracker.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brk718.tracker.ui.add.AddScreen
import com.brk718.tracker.ui.add.BarcodeScannerScreen
import com.brk718.tracker.ui.ads.AdManager
import com.brk718.tracker.ui.auth.AmazonAuthScreen
import com.brk718.tracker.ui.detail.DetailScreen
import com.brk718.tracker.ui.gmail.GmailScreen
import com.brk718.tracker.ui.home.HomeScreen
import com.brk718.tracker.ui.onboarding.OnboardingScreen
import com.brk718.tracker.ui.paywall.PaywallScreen
import com.brk718.tracker.ui.settings.ArchivedScreen
import com.brk718.tracker.ui.settings.SettingsScreen
import com.brk718.tracker.ui.settings.SettingsViewModel
import com.brk718.tracker.ui.theme.TrackerTheme

object Routes {
    const val ONBOARDING  = "onboarding"
    const val LIST        = "list"
    const val ADD         = "add"
    const val DETAIL      = "detail/{shipmentId}"
    const val GMAIL       = "gmail"
    const val AMAZON_AUTH = "amazon_auth"
    const val SETTINGS    = "settings"
    const val ARCHIVED    = "archived"
    const val PAYWALL     = "paywall"
    const val BARCODE_SCANNER = "barcode_scanner"
    fun detail(id: String) = "detail/$id"
}

private const val TRANSITION_DURATION = 300

@Composable
fun App(
    adManager: AdManager? = null,
    initialShipmentId: String? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val isPremium = settingsState.preferences.isPremium
    val onboardingDone = settingsState.preferences.onboardingDone
    val preferencesLoaded = settingsState.preferencesLoaded
    val darkTheme = when (settingsState.preferences.theme) {
        "light" -> false
        "dark"  -> true
        else    -> isSystemInDarkTheme()
    }

    TrackerTheme(darkTheme = darkTheme) {
        // Solo redirigir a onboarding cuando las preferencias ya cargaron de DataStore
        // (evitar falso negativo con el valor inicial por defecto false)
        LaunchedEffect(preferencesLoaded, onboardingDone) {
            if (preferencesLoaded && !onboardingDone) {
                navController.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.LIST) { inclusive = true }
                }
            }
        }
        // Navegar al detalle directamente si llegamos desde una notificación
        LaunchedEffect(initialShipmentId) {
            initialShipmentId?.let { navController.navigate(Routes.detail(it)) }
        }

        NavHost(
            navController    = navController,
            startDestination = Routes.LIST,
            // Transición por defecto para todas las pantallas
            enterTransition  = {
                slideIntoContainer(
                    towards   = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(TRANSITION_DURATION)
                )
            },
            exitTransition   = {
                fadeOut(animationSpec = tween(TRANSITION_DURATION / 2))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(TRANSITION_DURATION / 2))
            },
            popExitTransition  = {
                slideOutOfContainer(
                    towards   = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(TRANSITION_DURATION)
                )
            }
        ) {
            // La pantalla principal entra con fade (sin slide, es el destino raíz)
            composable(
                route        = Routes.LIST,
                enterTransition  = { fadeIn(tween(TRANSITION_DURATION)) },
                exitTransition   = { fadeOut(tween(TRANSITION_DURATION / 2)) },
                popEnterTransition = { fadeIn(tween(TRANSITION_DURATION)) },
                popExitTransition  = { fadeOut(tween(TRANSITION_DURATION / 2)) }
            ) {
                HomeScreen(
                    onAddClick        = { navController.navigate(Routes.ADD) },
                    onShipmentClick   = { id -> navController.navigate(Routes.detail(id)) },
                    onGmailClick      = { navController.navigate(Routes.GMAIL) },
                    onAmazonAuthClick = { navController.navigate(Routes.AMAZON_AUTH) },
                    onSettingsClick   = { navController.navigate(Routes.SETTINGS) },
                    onUpgradeClick    = { navController.navigate(Routes.PAYWALL) },
                    isPremium         = isPremium,
                    adManager         = adManager
                )
            }
            composable(
                route = Routes.ONBOARDING,
                enterTransition  = { fadeIn(tween(TRANSITION_DURATION)) },
                exitTransition   = { fadeOut(tween(TRANSITION_DURATION)) },
                popEnterTransition = { fadeIn(tween(TRANSITION_DURATION)) },
                popExitTransition  = { fadeOut(tween(TRANSITION_DURATION)) }
            ) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.LIST) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.ADD) { backStackEntry ->
                // Leer el código escaneado que devuelve BarcodeScannerScreen
                val scannedBarcode = backStackEntry
                    .savedStateHandle
                    .get<String>("scanned_barcode")
                AddScreen(
                    onBack         = { navController.popBackStack() },
                    onSuccess      = { navController.popBackStack() },
                    onUpgradeClick = { navController.navigate(Routes.PAYWALL) },
                    onScanClick    = { navController.navigate(Routes.BARCODE_SCANNER) },
                    scannedBarcode = scannedBarcode
                )
            }
            composable(Routes.BARCODE_SCANNER) {
                BarcodeScannerScreen(
                    onBarcodeDetected = { barcode ->
                        // Pasar el resultado de vuelta a AddScreen via SavedStateHandle
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_barcode", barcode)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PAYWALL) {
                PaywallScreen(onBack = { navController.popBackStack() })
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
                ArchivedScreen(
                    onBack         = { navController.popBackStack() },
                    onUpgradeClick = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(
                route     = Routes.DETAIL,
                arguments = listOf(navArgument("shipmentId") { type = NavType.StringType })
            ) {
                // Mostrar interstitial al abrir detalle (máx cada 3 veces, solo free)
                LaunchedEffect(Unit) {
                    adManager?.onDetailScreenOpened(context as Activity, isPremium)
                }
                DetailScreen(
                    onBack            = { navController.popBackStack() },
                    onAmazonAuthClick = { navController.navigate(Routes.AMAZON_AUTH) },
                    onUpgradeClick    = { navController.navigate(Routes.PAYWALL) }
                )
            }
        }
    }
}
