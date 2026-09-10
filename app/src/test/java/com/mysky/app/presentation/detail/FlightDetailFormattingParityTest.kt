package com.mysky.app.presentation.detail

import com.mysky.app.LISBON
import com.mysky.app.aircraft
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.Route
import com.mysky.app.overheadFlight
import com.mysky.app.presentation.format.FlightFormatting
import com.mysky.app.presentation.format.RouteFormatting
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SC-002: o detalhe e a lista mostram os mesmos valores para a mesma aeronave.
 *
 * **O que este teste prova e o que não prova.** Os dois ecrãs são Compose e o projeto só corre
 * testes na JVM, por isso nenhum teste daqui inspeciona o que é desenhado. O que se verifica é o
 * degrau onde a divergência nasceria: que existe **uma só** função por grandeza, e que a escolha de
 * qual altitude entra na conta é a mesma dos dois lados. Que ambos os ecrãs a chamam fica para a
 * revisão de código e para o passo 5.2 do quickstart, onde se comparam os dois ecrãs a olho.
 */
class FlightDetailFormattingParityTest {

    private val locale = Locale.forLanguageTag("pt-PT")

    private fun aircraftWith(geometric: Double?, barometric: Double?): Aircraft = aircraft(
        icao24 = "aaa111",
        position = LISBON,
        geometricAltitudeMeters = geometric,
        barometricAltitudeMeters = barometric,
    )

    @Test
    fun `a altitude que a lista mostra e a geometrica quando existe`() {
        // O detalhe assinala esta como "usada no cálculo"; a lista mostra-a sem qualificação. Se as
        // duas escolhas divergissem, o utilizador atento veria os ecrãs a discordar e concluiria
        // que um deles está errado.
        val aircraft = aircraftWith(geometric = 10_400.0, barometric = 10_350.0)

        assertEquals(10_400.0, aircraft.altitudeMeters!!, 0.001)
    }

    @Test
    fun `sem altitude geometrica os dois ecras caem para a barometrica`() {
        val aircraft = aircraftWith(geometric = null, barometric = 10_350.0)

        assertEquals(10_350.0, aircraft.altitudeMeters!!, 0.001)
    }

    @Test
    fun `distancia velocidade e elevacao passam pela mesma funcao nos dois ecras`() {
        // Uma segunda camada de formatação divergiria no primeiro arredondamento, e a diferença
        // seria indistinguível de um erro de cálculo para quem olha.
        val flight = overheadFlight(
            aircraft = aircraft(groundSpeedMetersPerSecond = 233.0),
            horizontalDistanceMeters = 12_345.0,
            elevationDegrees = 62.4,
        )

        assertEquals("12,3", FlightFormatting.distance(flight.horizontalDistanceMeters, locale = locale))
        assertEquals("839", FlightFormatting.speed(flight.aircraft.groundSpeedMetersPerSecond!!, locale = locale))
        assertEquals("62", FlightFormatting.elevationDegrees(flight.elevationDegrees, locale))
    }

    @Test
    fun `a rota e o mesmo par nos dois ecras, ou ausente nos dois`() {
        // FR-002 e SC-003: os dois ecrãs leem o mesmo campo pela mesma função. Se um deles
        // compusesse o par à sua maneira, a divergência apareceria como um separador ou uma ordem
        // diferentes — indistinguível de um erro de dados para quem olha.
        val comRota = overheadFlight(route = Route("LIS", "CDG"))
        val semRota = overheadFlight(route = null)

        assertEquals("LIS" to "CDG", RouteFormatting.pairOrNull(comRota.route))
        assertEquals(null, RouteFormatting.pairOrNull(semRota.route))
    }

    @Test
    fun `a regra do zenite aplica-se a direcao a olhar nos dois ecras`() {
        val flight = overheadFlight(bearingDegrees = 45.0, elevationDegrees = 89.0)

        assertEquals(
            null,
            FlightFormatting.compassPointOrNull(flight.bearingDegrees, flight.elevationDegrees),
        )
    }
}
