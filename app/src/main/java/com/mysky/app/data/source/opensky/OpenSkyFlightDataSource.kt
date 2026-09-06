package com.mysky.app.data.source.opensky

import com.mysky.app.data.mapper.toDomain
import com.mysky.app.data.source.FlightDataSource
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.BoundingBox
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação de [FlightDataSource] sobre a OpenSky Network.
 *
 * Não trata rate limiting: um 429 sobe como `HttpException` e é o repositório que o traduz em
 * `SkyError.RateLimited` (AD-010). Reintentar aqui, em silêncio, gastaria o orçamento diário
 * exatamente quando ele já se esgotou e esconderia do utilizador a razão de a lista não atualizar.
 */
@Singleton
class OpenSkyFlightDataSource @Inject constructor(
    private val api: OpenSkyApi,
) : FlightDataSource {

    override val id: String = SOURCE_ID

    override suspend fun fetchAircraftIn(box: BoundingBox): List<Aircraft> {
        val response = api.getStates(
            minLatitude = box.minLatitude,
            minLongitude = box.minLongitude,
            maxLatitude = box.maxLatitude,
            maxLongitude = box.maxLongitude,
        )
        // `states` vem `null` (e não vazio) quando não há tráfego: é uma resposta com sucesso,
        // não um erro. Confundir os dois faria um céu vazio parecer uma falha do serviço.
        return response.states.orEmpty().mapNotNull { state ->
            OpenSkyStateVector.parse(state).toDomain()
        }
    }

    companion object {
        const val SOURCE_ID = "opensky"
    }
}
