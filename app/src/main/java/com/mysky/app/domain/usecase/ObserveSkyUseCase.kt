package com.mysky.app.domain.usecase

import com.mysky.app.domain.geo.GeoCalculator
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadCriteria
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.Route
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.AirlineDirectory
import com.mysky.app.domain.repository.FlightRepository
import com.mysky.app.domain.repository.RouteDirectory
import com.mysky.app.domain.time.TimeProvider
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    private val routeDirectory: RouteDirectory,
    private val timeProvider: TimeProvider,
) {
    /**
     * Nada sai daqui por lançamento, nem sequer o que é culpa de quem chama.
     *
     * `Result.map` executa a transformação sem a proteger, e `boundingBoxesAround` valida o raio
     * com um `require`. Hoje o único chamador passa os critérios por omissão, mas AD-009 diz que a
     * feature de definições passará a alimentar este parâmetro sem mudar a assinatura — e um raio
     * inválido vindo de lá subiria pelo laço do ViewModel até rebentar o processo, em vez de
     * aparecer como um erro no ecrã. AD-010 promete `SkyError` dentro de `Result`; é aqui que essa
     * promessa se cumpre para tudo o que acontece acima da fronteira do repositório.
     */
    suspend operator fun invoke(
        observer: GeoPosition,
        criteria: OverheadCriteria = OverheadCriteria(),
    ): Result<List<OverheadFlight>> = try {
        // Duas caixas quando o raio cruza o antimeridiano; a deduplicação das aeronaves que
        // aparecem nas duas é do repositório, que é quem sabe que a consulta foi partida.
        val boxes = geoCalculator.boundingBoxesAround(
            center = observer,
            radiusMeters = criteria.maxHorizontalDistanceMeters,
        )

        flightRepository.getAircraftIn(boxes).map { aircraft ->
            detectOverheadFlights(
                observer = observer,
                aircraft = aircraft,
                criteria = criteria,
                nowEpochSeconds = timeProvider.nowEpochSeconds(),
            ).let { flights -> enrich(flights) }
        }
    } catch (cancellation: CancellationException) {
        // Cancelamento não é falha: é o ecrã a deixar de estar visível (FR-019).
        throw cancellation
    } catch (throwable: Exception) {
        Result.failure(throwable as? SkyError ?: SkyError.Unexpected(throwable))
    }

    /**
     * Acrescenta operador e rota a cada voo, tudo **em concorrência**.
     *
     * A forma importa. A consulta de operador é uma leitura de mapa em memória depois do primeiro
     * carregamento — grátis; a de rota é **sempre** I/O de disco. Encadear as duas por aeronave, e
     * as aeronaves umas atrás das outras, seria multiplicar por duas dezenas um custo que assim
     * fica pelo da consulta mais lenta. É o género de diferença que não aparece num teste unitário
     * e aparece como atraso no dispositivo (AD-015).
     *
     * A ordem da lista é preservada: `awaitAll` devolve pela ordem em que as tarefas foram criadas,
     * e a ordenação por elevação vem de trás, do caso de uso de deteção.
     */
    private suspend fun enrich(flights: List<OverheadFlight>): List<OverheadFlight> =
        coroutineScope {
            flights.map { flight ->
                async {
                    val callsign = flight.aircraft.callsign
                    val airline = async { resolveAirline(callsign) }
                    val route = async { resolveRoute(callsign) }
                    flight.copy(airline = airline.await(), route = route.await())
                }
            }.awaitAll()
        }

    /**
     * A rota é decoração, como o operador: se a tabela falhar, a aeronave aparece sem ela.
     *
     * O `RouteDirectory` promete não lançar, mas a promessa é dele e a consequência seria nossa —
     * esconder o céu inteiro por causa de duas siglas.
     */
    private suspend fun resolveRoute(callsign: String?): Route? = try {
        routeDirectory.findByCallsign(callsign)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Exception) {
        null
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
