package com.mysky.app.presentation.main

import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkySettings

/**
 * Estado do ecrã principal. Um único data class imutável em vez de vários `StateFlow`, para que a
 * UI nunca observe combinações impossíveis.
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val flights: List<OverheadFlight> = emptyList(),
    val settings: SkySettings = SkySettings(),
    val locationPermissionGranted: Boolean = false,
    val lastUpdatedEpochSeconds: Long? = null,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && flights.isEmpty() && errorMessage == null
}
