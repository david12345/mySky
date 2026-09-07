package com.mysky.app.data.repository

import com.mysky.app.data.local.SightingDao
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.repository.SightingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * TODO(feature/sightings): implementar mapeamento entidade <-> domínio e política de retenção.
 */
@Singleton
class SightingRepositoryImpl @Inject constructor(
    private val sightingDao: SightingDao,
) : SightingRepository {

    override fun recentSightings(limit: Int): Flow<List<OverheadFlight>> {
        TODO("Implementar durante a feature 'detalhe do avião' (ver .specify/)")
    }

    override suspend fun record(flight: OverheadFlight, observedAtEpochSeconds: Long) {
        TODO("Implementar durante a feature 'detalhe do avião' (ver .specify/)")
    }

    override suspend fun wasNotifiedRecently(icao24: String, withinSeconds: Long): Boolean {
        TODO("Implementar durante a feature 'notificações' (ver .specify/)")
    }
}
