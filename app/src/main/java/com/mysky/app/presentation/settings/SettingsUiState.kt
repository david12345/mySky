package com.mysky.app.presentation.settings

import com.mysky.app.domain.model.RouteUpdateState

/**
 * Estado do ecrã de definições. Um único data class, como manda o `CLAUDE.md`.
 *
 * Hoje só tem a tabela de rotas — é a primeira entrada real deste ecrã. A feature de definições,
 * quando existir, **estende esta classe** com o raio, a elevação mínima e as unidades, em vez de
 * criar um segundo `StateFlow` solto ao lado (AD-017).
 */
data class SettingsUiState(
    /** `null` enquanto o cabeçalho ainda não foi lido — não significa que não haja tabela. */
    val routeTableGeneratedAtEpochSeconds: Long? = null,
    val routeCount: Int = 0,
    val updateState: RouteUpdateState = RouteUpdateState.Idle,
) {
    val isUpdating: Boolean get() = updateState is RouteUpdateState.InProgress

    val hasTableInfo: Boolean get() = routeTableGeneratedAtEpochSeconds != null
}
