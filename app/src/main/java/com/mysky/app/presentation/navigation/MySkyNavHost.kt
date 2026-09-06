package com.mysky.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mysky.app.presentation.detail.FlightDetailScreen
import com.mysky.app.presentation.main.MainScreen
import com.mysky.app.presentation.settings.SettingsScreen

/** Destinos da app. O detalhe recebe o `icao24`, identificador estável de uma aeronave. */
object MySkyRoutes {
    const val SKY = "sky"
    const val SETTINGS = "settings"
    const val FLIGHT_DETAIL = "flight/{icao24}"

    fun flightDetail(icao24: String): String = "flight/$icao24"
}

@Composable
fun MySkyNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = MySkyRoutes.SKY) {
        composable(MySkyRoutes.SKY) {
            MainScreen(
                onFlightClick = { icao24 -> navController.navigate(MySkyRoutes.flightDetail(icao24)) },
                onSettingsClick = { navController.navigate(MySkyRoutes.SETTINGS) },
            )
        }
        composable(MySkyRoutes.FLIGHT_DETAIL) { FlightDetailScreen(onBack = navController::popBackStack) }
        composable(MySkyRoutes.SETTINGS) { SettingsScreen(onBack = navController::popBackStack) }
    }
}
