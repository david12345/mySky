package com.mysky.app.presentation.settings

import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.model.SkyBudget
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

    // --- O que a cadência do widget custa (005-sky-widget) --------------------------------------

    /**
     * Consultas por dia que o trabalho de fundo gasta com a cadência escolhida.
     *
     * Derivado do `SkyBudget` e não recalculado aqui (AD-027). O orçamento de 400 vive num sítio só:
     * o defeito que a revisão da 004 encontrou foi um número escrito de duas maneiras que divergiu.
     */
    val widgetQueriesPerDay: Int
        get() = SkyBudget.queriesPerDay(settings.refreshIntervalMinutes)

    /** Fatia do orçamento diário, entre 0 e 1. */
    val widgetBudgetShare: Double
        get() = SkyBudget.budgetShare(settings.refreshIntervalMinutes)

    /**
     * Quanto tempo de ecrã aberto sobra por dia depois de o widget se servir.
     *
     * É o número que o utilizador não tem como descobrir sozinho: escolhe uma cadência mais
     * frequente e, semanas depois, a app deixa de atualizar mais cedo à tarde sem relação aparente.
     */
    val remainingScreenSeconds: Long
        get() = SkyBudget.remainingScreenSeconds(settings.refreshIntervalMinutes)

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
