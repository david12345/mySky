package com.mysky.app.domain.repository

import com.mysky.app.domain.model.OverheadFlight
import kotlinx.coroutines.flow.Flow

/**
 * Histórico de avistamentos, usado pelo ecrã de detalhe (trajeto recente) e para evitar
 * notificações repetidas para a mesma passagem.
 */
interface SightingRepository {
    fun recentSightings(limit: Int = 50): Flow<List<OverheadFlight>>

    /**
     * @param notified se esta aeronave foi avisada ao utilizador. **Sem valor por omissão**, de
     *   propósito: quando a feature do histórico chegar e começar a gravar avistamentos não avisados,
     *   o compilador obriga cada chamador a dizer o que quer, em vez de herdar em silêncio um
     *   significado que deixou de ser o certo.
     */
    suspend fun record(flight: OverheadFlight, observedAtEpochSeconds: Long, notified: Boolean)

    /** `true` se já houve notificação para esta aeronave dentro da janela indicada. */
    suspend fun wasNotifiedRecently(icao24: String, withinSeconds: Long): Boolean
}
