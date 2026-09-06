package com.mysky.app.data.repository

import com.mysky.app.data.source.FlightDataSource
import com.mysky.app.di.IoDispatcher
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.BoundingBox
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.FlightRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Implementação de [FlightRepository] sobre uma [FlightDataSource] injetada.
 *
 * Deliberadamente **não** se chama `OpenSkyRepositoryImpl`: o repositório é agnóstico da fonte e
 * trocar de fonte (ou combinar várias) faz-se em `di/DataSourceModule`, sem alterar esta classe
 * nem nada acima dela.
 *
 * É aqui que as exceções de rede morrem: acima desta fronteira só circulam [SkyError] dentro de
 * `Result`.
 */
@Singleton
class FlightRepositoryImpl @Inject constructor(
    private val dataSource: FlightDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FlightRepository {

    override suspend fun getAircraftIn(boxes: List<BoundingBox>): Result<List<Aircraft>> =
        withContext(ioDispatcher) {
            try {
                val aircraft = coroutineScope {
                    boxes.map { box -> async { dataSource.fetchAircraftIn(box) } }.awaitAll()
                }.flatten()
                Result.success(aircraft.deduplicateByIcao24())
            } catch (cancellation: CancellationException) {
                // Cancelamento não é falha: é o ecrã a deixar de estar visível (FR-019). Engoli-lo
                // aqui deixaria o laço do ViewModel a tratar a sua própria paragem como um erro.
                throw cancellation
            } catch (throwable: Exception) {
                Result.failure(throwable.toSkyError())
            }
        }

    /**
     * A mesma aeronave aparece nas duas caixas quando o círculo do observador cruza o
     * antimeridiano. Em duplicado ganha o vetor mais recente — a divisão em caixas é artefacto de
     * como a fonte é interrogada, e o contrato promete a quem chama uma lista já sem repetições.
     */
    private fun List<Aircraft>.deduplicateByIcao24(): List<Aircraft> =
        groupingBy { it.icao24 }
            .reduce { _, newest, candidate ->
                if (candidate.contactOrOldest > newest.contactOrOldest) candidate else newest
            }
            .values
            .toList()

    private val Aircraft.contactOrOldest: Long
        get() = lastContactEpochSeconds ?: Long.MIN_VALUE

    /**
     * Uma falha parcial faz falhar o todo: apresentar meio céu como se fosse o céu inteiro é pior
     * do que dizer que não se conseguiu obter — o utilizador procuraria na lista um avião que ela
     * nunca poderia conter.
     */
    private fun Throwable.toSkyError(): SkyError = when (this) {
        is SkyError -> this
        is IOException -> SkyError.NoConnection
        is HttpException -> when (code()) {
            HTTP_TOO_MANY_REQUESTS -> SkyError.RateLimited(retryAfterSeconds())
            else -> SkyError.FlightServiceUnavailable(code())
        }
        else -> SkyError.Unexpected(this)
    }

    /** A OpenSky usa um cabeçalho próprio; `Retry-After` é o padrão. Aceitam-se os dois. */
    private fun HttpException.retryAfterSeconds(): Long? {
        val headers = response()?.headers() ?: return null
        return RETRY_AFTER_HEADERS.firstNotNullOfOrNull { headers[it]?.toLongOrNull() }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        val RETRY_AFTER_HEADERS = listOf("X-Rate-Limit-Retry-After-Seconds", "Retry-After")
    }
}
