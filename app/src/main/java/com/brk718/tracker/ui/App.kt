package com.brk718.tracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brk718.tracker.ui.add.AddScreen
import com.brk718.tracker.ui.detail.DetailScreen
import com.brk718.tracker.ui.home.HomeScreen

object Routes {
    const val LIST = "list"
    const val ADD = "add"
    const val DETAIL = "detail/{shipmentId}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun App() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            HomeScreen(
                onAddClick = { navController.navigate(Routes.ADD) },
                onShipmentClick = { id -> navController.navigate(Routes.detail(id)) }
            )
        }
        composable(Routes.ADD) {
            AddScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("shipmentId") { type = NavType.StringType })
        ) {
            // El ViewModel tomará el ID del SavedStateHandle automáticamente
            DetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
