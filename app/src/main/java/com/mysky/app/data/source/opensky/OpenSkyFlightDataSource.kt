package com.mysky.app.data.source.opensky

import com.mysky.app.data.source.FlightDataSource
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.BoundingBox
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação de [FlightDataSource] sobre a OpenSky Network.
 *
 * TODO(feature/sky-list): chamar [OpenSkyApi.getStates], converter cada entrada com
 *  [OpenSkyStateVector.parse] + `toDomain()` e descartar as que não convertem.
 *  Tratar 429 (rate limit) com backoff — a versão anónima da API é agressiva a limitar.
 */
@Singleton
class OpenSkyFlightDataSource @Inject constructor(
    private val api: OpenSkyApi,
) : FlightDataSource {

    override val id: String = SOURCE_ID

    override suspend fun fetchAircraftIn(box: BoundingBox): List<Aircraft> {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }

    companion object {
        const val SOURCE_ID = "opensky"
    }
}
