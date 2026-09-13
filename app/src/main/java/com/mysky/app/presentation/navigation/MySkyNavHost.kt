package com.mysky.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mysky.app.presentation.detail.FlightDetailScreen
import com.mysky.app.presentation.detail.FlightDetailViewModel
import com.mysky.app.presentation.main.MainScreen
import com.mysky.app.presentation.settings.SettingsScreen

/** Destinos da app. O detalhe recebe o `icao24`, identificador estável de uma aeronave. */
object MySkyRoutes {
    const val SKY = "sky"
    const val SETTINGS = "settings"
    const val FLIGHT_DETAIL = "flight/{icao24}"

    /** Tem de coincidir com `OverheadNotifier.DEEP_LINK_PREFIX`. */
    const val FLIGHT_DETAIL_DEEP_LINK = "mysky://flight/{icao24}"

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
        composable(
            route = MySkyRoutes.FLIGHT_DETAIL,
            // Entrada a partir de uma notificação (FR-007): abre **aquela** aeronave, não a lista.
            // Um deep link e não um extra lido pela Activity, para a navegação continuar a ter um
            // único sítio onde as rotas são definidas.
            deepLinks = listOf(navDeepLink { uriPattern = MySkyRoutes.FLIGHT_DETAIL_DEEP_LINK }),
            // Argumento declarado e tipado: é o que garante que o `icao24` chega ao
            // `SavedStateHandle` do ViewModel do detalhe e identifica a aeronave certa (FR-028).
            arguments = listOf(
                navArgument(FlightDetailViewModel.ARG_ICAO24) { type = NavType.StringType },
            ),
        ) { FlightDetailScreen(onBack = navController::popBackStack) }
        composable(MySkyRoutes.SETTINGS) { SettingsScreen(onBack = navController::popBackStack) }
    }
}
