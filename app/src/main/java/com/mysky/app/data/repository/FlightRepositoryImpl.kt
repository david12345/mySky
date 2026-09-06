package com.mysky.app.data.repository

import com.mysky.app.data.source.FlightDataSource
import com.mysky.app.di.IoDispatcher
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.BoundingBox
import com.mysky.app.domain.repository.FlightRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Implementação de [FlightRepository] sobre uma [FlightDataSource] injetada.
 *
 * Deliberadamente **não** se chama `OpenSkyRepositoryImpl`: o repositório é agnóstico da fonte e
 * trocar de fonte (ou combinar várias) faz-se em `di/DataSourceModule`, sem alterar esta classe
 * nem nada acima dela.
 *
 * TODO(feature/sky-list): pedir todas as [BoundingBox] em paralelo, deduplicar por `icao24`
 *  (o mesmo avião aparece nas duas caixas quando o círculo cruza o antimeridiano) e mapear
 *  exceções para `Result.failure` com um tipo de erro de domínio.
 */
@Singleton
class FlightRepositoryImpl @Inject constructor(
    private val dataSource: FlightDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FlightRepository {

    override suspend fun getAircraftIn(boxes: List<BoundingBox>): Result<List<Aircraft>> {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }
}
