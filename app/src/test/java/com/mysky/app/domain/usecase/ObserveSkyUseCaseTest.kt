package com.mysky.app.domain.usecase

import com.mysky.app.LISBON
import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.aircraft
import com.mysky.app.domain.geo.GeoCalculator
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.Airline
import com.mysky.app.domain.model.BoundingBox
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadCriteria
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.AirlineDirectory
import com.mysky.app.domain.repository.FlightRepository
import com.mysky.app.domain.time.TimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveSkyUseCaseTest {

    private val flightRepository = mockk<FlightRepository>()
    private val airlineDirectory = mockk<AirlineDirectory>()
    private val geoCalculator = GeoCalculator()

    /** O tempo entra por abstração: nenhum teste lê o relógio do sistema (princípio I). */
    private val timeProvider = TimeProvider { NOW_EPOCH_SECONDS }

    private val useCase = ObserveSkyUseCase(
        flightRepository = flightRepository,
        geoCalculator = geoCalculator,
        detectOverheadFlights = DetectOverheadFlightsUseCase(geoCalculator),
        airlineDirectory = airlineDirectory,
        timeProvider = timeProvider,
    )

    /** Aeronave sobre o observador, a [altitudeMeters] de altitude e [offsetMeters] a norte. */
    private fun overhead(
        icao24: String,
        callsign: String? = "TAP1234",
        altitudeMeters: Double,
        offsetMeters: Double = 100.0,
    ): Aircraft = aircraft(
        icao24 = icao24,
        callsign = callsign,
        geometricAltitudeMeters = altitudeMeters,
        position = GeoPosition(
            latitudeDegrees = LISBON.latitudeDegrees + Math.toDegrees(offsetMeters / 6_371_008.8),
            longitudeDegrees = LISBON.longitudeDegrees,
        ),
    )

    private fun repositoryReturns(vararg aircraft: Aircraft) {
        coEvery { flightRepository.getAircraftIn(any()) } returns Result.success(aircraft.toList())
    }

    private fun directoryKnowsNothing() {
        coEvery { airlineDirectory.findByCallsign(any()) } returns null
    }

    @Test
    fun `resultado sai ordenado por elevacao decrescente`() = runTest {
        // Mesma distância horizontal, altitudes diferentes: quanto mais alto, maior a elevação.
        repositoryReturns(
            overhead("baixo", altitudeMeters = 1_000.0),
            overhead("alto", altitudeMeters = 11_000.0),
            overhead("medio", altitudeMeters = 5_000.0),
        )
        directoryKnowsNothing()

        val flights = useCase(LISBON).getOrThrow()

        assertEquals(listOf("alto", "medio", "baixo"), flights.map { it.aircraft.icao24 })
        assertTrue(flights.zipWithNext().all { (a, b) -> a.elevationDegrees >= b.elevationDegrees })
    }

    @Test
    fun `operador e resolvido a partir do indicativo`() = runTest {
        repositoryReturns(overhead("aaa111", callsign = "TAP1234", altitudeMeters = 10_000.0))
        coEvery { airlineDirectory.findByCallsign("TAP1234") } returns
            Airline("TAP", "TAP Air Portugal")

        val flight = useCase(LISBON).getOrThrow().single()

        assertEquals("TAP Air Portugal", flight.airline?.name)
    }

    @Test
    fun `prefixo desconhecido deixa a aeronave na lista sem operador`() = runTest {
        repositoryReturns(overhead("aaa111", callsign = "XXX9999", altitudeMeters = 10_000.0))
        directoryKnowsNothing()

        val flight = useCase(LISBON).getOrThrow().single()

        assertNull(flight.airline)
        assertEquals("XXX9999", flight.aircraft.callsign)
    }

    @Test
    fun `falha do diretorio de operadores nao falha a operacao`() = runTest {
        repositoryReturns(overhead("aaa111", altitudeMeters = 10_000.0))
        coEvery { airlineDirectory.findByCallsign(any()) } throws IllegalStateException("tabela ilegível")

        val result = useCase(LISBON)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().single().airline)
    }

    @Test
    fun `falha do repositorio propaga o mesmo SkyError`() = runTest {
        coEvery { flightRepository.getAircraftIn(any()) } returns
            Result.failure(SkyError.RateLimited(90L))

        val result = useCase(LISBON)

        assertEquals(SkyError.RateLimited(90L), result.exceptionOrNull())
    }

    @Test
    fun `a idade dos vetores e avaliada com o instante do TimeProvider`() = runTest {
        // Contacto mais antigo do que maxStateAgeSeconds face ao instante falso: tem de sair.
        repositoryReturns(
            overhead("recente", altitudeMeters = 10_000.0),
            aircraft(
                icao24 = "obsoleto",
                geometricAltitudeMeters = 10_000.0,
                position = LISBON,
                lastContactEpochSeconds = NOW_EPOCH_SECONDS - 600,
            ),
        )
        directoryKnowsNothing()

        val flights = useCase(LISBON).getOrThrow()

        assertEquals(listOf("recente"), flights.map { it.aircraft.icao24 })
    }

    @Test
    fun `aeronave em solo nunca aparece no ceu`() = runTest {
        repositoryReturns(
            aircraft(icao24 = "emsolo", position = LISBON, geometricAltitudeMeters = 5.0, onGround = true),
        )
        directoryKnowsNothing()

        assertTrue(useCase(LISBON).getOrThrow().isEmpty())
    }

    @Test
    fun `observador junto ao antimeridiano gera duas caixas sem duplicar aeronaves`() = runTest {
        val fiji = GeoPosition(latitudeDegrees = -17.75, longitudeDegrees = 179.99)
        val boxes = slot<List<BoundingBox>>()
        coEvery { flightRepository.getAircraftIn(capture(boxes)) } returns
            Result.success(listOf(aircraft(icao24 = "aaa111", position = fiji, geometricAltitudeMeters = 10_000.0)))
        directoryKnowsNothing()

        val flights = useCase(fiji).getOrThrow()

        assertEquals(2, boxes.captured.size)
        // Cada caixa é válida por si: nenhuma cruza o antimeridiano.
        assertTrue(boxes.captured.all { it.minLongitude <= it.maxLongitude })
        assertEquals(1, flights.size)
    }

    @Test
    fun `as caixas cobrem o raio dos criterios recebidos`() = runTest {
        val boxes = slot<List<BoundingBox>>()
        coEvery { flightRepository.getAircraftIn(capture(boxes)) } returns Result.success(emptyList())

        useCase(LISBON, OverheadCriteria(maxHorizontalDistanceMeters = 100_000.0))

        val box = boxes.captured.single()
        val latitudeSpanMeters = Math.toRadians(box.maxLatitude - box.minLatitude) * 6_371_008.8
        assertEquals(200_000.0, latitudeSpanMeters, 1_000.0)
    }

    @Test
    fun `ceu sem trafego devolve sucesso com lista vazia`() = runTest {
        repositoryReturns()

        val result = useCase(LISBON)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    // --- Nada sobe daqui por lançamento (AD-010) -------------------------------------------------

    @Test
    fun `excecao lancada pelo repositorio vira falha em vez de subir`() = runTest {
        // Um repositório que lança em vez de devolver failure é um contrato quebrado, mas o laço do
        // ViewModel não tem onde apanhar isto: rebentaria o processo em vez de mostrar um erro.
        coEvery { flightRepository.getAircraftIn(any()) } throws IOException("socket fechado")

        val error = useCase(LISBON).exceptionOrNull()

        assertTrue(error is SkyError.Unexpected)
        assertEquals("socket fechado", (error as SkyError.Unexpected).cause?.message)
    }

    @Test
    fun `SkyError lancado nao e embrulhado outra vez`() = runTest {
        coEvery { flightRepository.getAircraftIn(any()) } throws SkyError.NoConnection

        assertEquals(SkyError.NoConnection, useCase(LISBON).exceptionOrNull())
    }

    @Test
    fun `raio invalido nos criterios vira falha em vez de rebentar`() = runTest {
        // `boundingBoxesAround` valida o raio com um `require`. AD-009 prevê que a feature de
        // definições passe a alimentar estes critérios: um valor mau vindo de lá tem de aparecer no
        // ecrã como erro, não como crash.
        val result = useCase(LISBON, OverheadCriteria(maxHorizontalDistanceMeters = 0.0))

        assertTrue(result.exceptionOrNull() is SkyError.Unexpected)
    }

    @Test
    fun `excecao na detecao vira falha em vez de subir`() = runTest {
        // `Result.map` corre a transformação sem a proteger: sem o try/catch do caso de uso, uma
        // falha aqui dentro passava ao lado do `Result` e saía por lançamento.
        val detector = mockk<DetectOverheadFlightsUseCase>()
        every { detector(any(), any(), any(), any()) } throws IllegalStateException("geometria")
        val useCase = ObserveSkyUseCase(
            flightRepository = flightRepository,
            geoCalculator = geoCalculator,
            detectOverheadFlights = detector,
            airlineDirectory = airlineDirectory,
            timeProvider = timeProvider,
        )
        repositoryReturns(overhead("aaa111", altitudeMeters = 10_000.0))

        assertTrue(useCase(LISBON).exceptionOrNull() is SkyError.Unexpected)
    }

    @Test
    fun `cancelamento sobe em vez de virar falha`() = runTest {
        // Cancelar é o ecrã a deixar de estar visível (FR-019), não um erro para mostrar. Convertê-lo
        // em `Result.failure` faria a UI piscar um erro sempre que a app fosse para segundo plano.
        coEvery { flightRepository.getAircraftIn(any()) } throws CancellationException("ecrã escondido")

        var propagated = false
        try {
            useCase(LISBON)
        } catch (cancellation: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }
}
