package com.mysky.app.domain.usecase

import com.mysky.app.LISBON
import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.aircraft
import com.mysky.app.domain.geo.GeoCalculator
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.Route
import com.mysky.app.domain.repository.AirlineDirectory
import com.mysky.app.domain.repository.FlightRepository
import com.mysky.app.domain.repository.RouteDirectory
import com.mysky.app.domain.time.TimeProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O enriquecimento resolve-se em concorrência, não em cadeia (AD-015).
 *
 * Não é micro-otimização. A consulta de operador é uma leitura de mapa em memória; a de rota é
 * **sempre** I/O de disco. Encadear as duas por aeronave, e as aeronaves umas atrás das outras,
 * multiplicaria por várias dezenas um custo que assim fica pelo da consulta mais lenta — e é o
 * género de diferença que não aparece num teste de comportamento e aparece como atraso no
 * dispositivo.
 *
 * Em tempo virtual, a diferença é aritmética: sequencial dá a soma, concorrente dá o máximo.
 */
class ObserveSkyEnrichmentConcurrencyTest {

    private val flightRepository = mockk<FlightRepository>()
    private val airlineDirectory = mockk<AirlineDirectory>()
    private val routeDirectory = mockk<RouteDirectory>()
    private val geoCalculator = GeoCalculator()

    private val useCase = ObserveSkyUseCase(
        flightRepository = flightRepository,
        geoCalculator = geoCalculator,
        detectOverheadFlights = DetectOverheadFlightsUseCase(geoCalculator),
        airlineDirectory = airlineDirectory,
        routeDirectory = routeDirectory,
        timeProvider = TimeProvider { NOW_EPOCH_SECONDS },
    )

    private fun overhead(index: Int): Aircraft = aircraft(
        icao24 = "aaa%03d".format(index),
        callsign = "TAP%04d".format(index),
        geometricAltitudeMeters = 10_000.0,
        position = GeoPosition(
            latitudeDegrees = LISBON.latitudeDegrees + Math.toDegrees(100.0 / 6_371_008.8),
            longitudeDegrees = LISBON.longitudeDegrees,
        ),
    )

    @Test
    fun `operador e rota de dezenas de aeronaves resolvem-se em concorrencia`() = runTest {
        val aircraft = (1..AIRCRAFT_COUNT).map(::overhead)
        coEvery { flightRepository.getAircraftIn(any()) } returns Result.success(aircraft)
        coEvery { airlineDirectory.findByCallsign(any()) } coAnswers {
            delay(LOOKUP_MILLIS)
            Airline("TAP", "TAP Air Portugal")
        }
        coEvery { routeDirectory.findByCallsign(any()) } coAnswers {
            delay(LOOKUP_MILLIS)
            Route("LIS", "CDG")
        }

        val before = testScheduler.currentTime
        val flights = useCase(LISBON).getOrThrow()
        val elapsed = testScheduler.currentTime - before

        assertEquals(AIRCRAFT_COUNT, flights.size)
        assertTrue("todas as aeronaves enriquecidas", flights.all { it.airline != null && it.route != null })

        // Encadeado seriam 2 × 40 × 50 ms = 4 000 ms. Concorrente é o custo de uma consulta.
        val sequentialCost = 2L * AIRCRAFT_COUNT * LOOKUP_MILLIS
        assertTrue(
            "enriquecimento demorou $elapsed ms; sequencial daria $sequentialCost ms",
            elapsed < sequentialCost / 4,
        )
        assertEquals(LOOKUP_MILLIS, elapsed)
    }

    private companion object {
        const val AIRCRAFT_COUNT = 40
        const val LOOKUP_MILLIS = 50L
    }
}
