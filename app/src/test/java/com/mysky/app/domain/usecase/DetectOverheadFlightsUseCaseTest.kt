package com.mysky.app.domain.usecase

import com.mysky.app.domain.geo.GeoCalculator
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadCriteria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O caso de uso central corre inteiramente na JVM: sem rede, sem Android, sem mocks de sistema.
 * Qualquer regra nova de "está no meu céu" tem de aparecer aqui primeiro.
 */
class DetectOverheadFlightsUseCaseTest {

    private val useCase = DetectOverheadFlightsUseCase(GeoCalculator())
    private val observer = GeoPosition(38.7223, -9.1393)
    private val now = 1_700_000_000L

    private fun aircraft(
        icao24: String = "abc123",
        position: GeoPosition? = observer,
        altitude: Double? = 10_000.0,
        onGround: Boolean = false,
        lastContact: Long? = now,
    ) = Aircraft(
        icao24 = icao24,
        callsign = "TAP1234",
        position = position,
        geometricAltitudeMeters = altitude,
        onGround = onGround,
        lastContactEpochSeconds = lastContact,
    )

    @Test
    fun `aviao mesmo por cima e detetado`() {
        val result = useCase(observer, listOf(aircraft()), nowEpochSeconds = now)
        assertEquals(1, result.size)
        assertEquals(90.0, result.single().elevationDegrees, 0.001)
    }

    @Test
    fun `aviao no solo e ignorado`() {
        val result = useCase(observer, listOf(aircraft(onGround = true)), nowEpochSeconds = now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `aviao sem posicao e ignorado`() {
        val result = useCase(observer, listOf(aircraft(position = null)), nowEpochSeconds = now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `aviao abaixo da altitude minima e ignorado`() {
        val result = useCase(observer, listOf(aircraft(altitude = 100.0)), nowEpochSeconds = now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `vetor de estado obsoleto e ignorado`() {
        val stale = aircraft(lastContact = now - 10_000)
        val result = useCase(observer, listOf(stale), nowEpochSeconds = now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `aviao perto mas baixo no ceu nao conta como por cima`() {
        // 20 km a norte, a 3000 m: elevação ~8,5 graus, abaixo do limiar de 25.
        val far = GeoPosition(observer.latitudeDegrees + 0.18, observer.longitudeDegrees)
        val result = useCase(
            observer = observer,
            aircraft = listOf(aircraft(position = far, altitude = 3_000.0)),
            criteria = OverheadCriteria(),
            nowEpochSeconds = now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resultado vem ordenado por elevacao decrescente`() {
        val overhead = aircraft(icao24 = "aaa111")
        val lower = aircraft(
            icao24 = "bbb222",
            position = GeoPosition(observer.latitudeDegrees + 0.05, observer.longitudeDegrees),
        )
        val result = useCase(observer, listOf(lower, overhead), nowEpochSeconds = now)
        assertEquals(listOf("aaa111", "bbb222"), result.map { it.aircraft.icao24 })
    }
}
