package com.mysky.app.presentation.settings

import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.model.SkyRange
import com.mysky.app.domain.model.SkySettings

/**
 * Estado do ecrã de definições. Um único data class, como manda o `CLAUDE.md`.
 *
 * A feature das rotas fixou na AD-017 que este ecrã cresce **estendendo esta classe**, em vez de
 * criar um segundo estado ao lado. É o que se faz aqui: os campos da tabela de rotas ficam onde
 * estavam, e as preferências entram como um campo mais.
 */
data class SettingsUiState(
    // Tabela de rotas (003-flight-route)
    val routeTableGeneratedAtEpochSeconds: Long? = null,
    val routeCount: Int = 0,
    val updateState: RouteUpdateState = RouteUpdateState.Idle,
    // Preferências (004-settings)
    val settings: SkySettings = SkySettings(),
) {
    val isUpdating: Boolean get() = updateState is RouteUpdateState.InProgress

    val hasTableInfo: Boolean get() = routeTableGeneratedAtEpochSeconds != null

    /** Até onde vale a pena procurar, dado o ângulo escolhido. */
    val usefulRangeMeters: Double
        get() = SkyRange.usefulRangeMeters(settings.minElevationDegrees)

    /**
     * O raio escolhido vai além do que o ângulo torna visível?
     *
     * É a única forma de o utilizador descobrir que as duas definições interagem: sem este aviso,
     * põe o raio no máximo, não vê aeronave nova nenhuma, e conclui que a app está estragada
     * (FR-014).
     */
    val radiusExceedsUsefulRange: Boolean
        get() = SkyRange.radiusExceedsUsefulRange(
            radiusMeters = settings.detectionRadiusMeters,
            minElevationDegrees = settings.minElevationDegrees,
        )

    /** Quais os valores que estão como vieram de fábrica (FR-012). */
    val isRadiusAtDefault: Boolean
        get() = settings.detectionRadiusMeters == SkySettings().detectionRadiusMeters

    val isMinElevationAtDefault: Boolean
        get() = settings.minElevationDegrees == SkySettings().minElevationDegrees

    val isMinAltitudeAtDefault: Boolean
        get() = settings.minAltitudeMeters == SkySettings().minAltitudeMeters

    val isAtDefaults: Boolean
        get() = settings.withDefaults() == settings
}
