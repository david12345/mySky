package com.mysky.app.domain.repository

import com.mysky.app.domain.model.OverheadFlight
import kotlinx.coroutines.flow.Flow

/**
 * Histórico de avistamentos, usado pelo ecrã de detalhe (trajeto recente) e para evitar
 * notificações repetidas para a mesma passagem.
 */
interface SightingRepository {
    fun recentSightings(limit: Int = 50): Flow<List<OverheadFlight>>

    suspend fun record(flight: OverheadFlight, observedAtEpochSeconds: Long)

    /** `true` se já houve notificação para esta aeronave dentro da janela indicada. */
    suspend fun wasNotifiedRecently(icao24: String, withinSeconds: Long): Boolean
}
