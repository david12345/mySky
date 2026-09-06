package com.mysky.app.domain.usecase

import com.mysky.app.domain.geo.GeoCalculator
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadCriteria
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.repository.AirlineDirectory
import com.mysky.app.domain.repository.FlightRepository
import com.mysky.app.domain.time.TimeProvider
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Compõe fonte de voos + [DetectOverheadFlightsUseCase] + tabela de operadores numa única
 * operação, partilhada pelo ecrã principal, pelo widget e pelo worker de notificações.
 *
 * Recebe os [OverheadCriteria] por parâmetro em vez de os ir buscar às definições (AD-009): nesta
 * feature são os valores por omissão, e quando a feature de definições existir é o ViewModel que
 * passa a alimentar o parâmetro — sem mudar esta assinatura.
 */
class ObserveSkyUseCase @Inject constructor(
    private val flightRepository: FlightRepository,
    private val geoCalculator: GeoCalculator,
    private val detectOverheadFlights: DetectOverheadFlightsUseCase,
    private val airlineDirectory: AirlineDirectory,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        observer: GeoPosition,
        criteria: OverheadCriteria = OverheadCriteria(),
    ): Result<List<OverheadFlight>> {
        // Duas caixas quando o raio cruza o antimeridiano; a deduplicação das aeronaves que
        // aparecem nas duas é do repositório, que é quem sabe que a consulta foi partida.
        val boxes = geoCalculator.boundingBoxesAround(
            center = observer,
            radiusMeters = criteria.maxHorizontalDistanceMeters,
        )

        return flightRepository.getAircraftIn(boxes).map { aircraft ->
            detectOverheadFlights(
                observer = observer,
                aircraft = aircraft,
                criteria = criteria,
                nowEpochSeconds = timeProvider.nowEpochSeconds(),
            ).map { flight -> flight.copy(airline = resolveAirline(flight.aircraft.callsign)) }
        }
    }

    /**
     * O nome do operador é decoração: se a tabela falhar, a aeronave aparece na lista sem nome de
     * companhia. Deixar uma falha aqui derrubar a operação inteira esconderia todo o céu por causa
     * de um campo que o utilizador consegue dispensar.
     */
    private suspend fun resolveAirline(callsign: String?): Airline? = try {
        airlineDirectory.findByCallsign(callsign)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Exception) {
        null
    }
}
