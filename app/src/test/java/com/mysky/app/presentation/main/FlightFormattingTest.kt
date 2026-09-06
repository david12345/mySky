package com.mysky.app.presentation.main

import com.mysky.app.presentation.main.format.CompassPoint
import com.mysky.app.presentation.main.format.FlightFormatting
import com.mysky.app.presentation.main.format.Freshness
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlightFormattingTest {

    /** Fixo em vez de `Locale.getDefault()`: senão o resultado dependia da máquina que corre. */
    private val pt = Locale.forLanguageTag("pt-PT")

    /** pt-PT separa milhares com espaço fino; o teste não é sobre qual espaço em concreto. */
    private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `distancia sai em quilometros com uma casa decimal`() {
        assertEquals("12,4", FlightFormatting.distanceKm(12_432.0, pt))
        assertEquals("0,3", FlightFormatting.distanceKm(300.0, pt))
        assertEquals("5,0", FlightFormatting.distanceKm(5_000.0, pt))
    }

    @Test
    fun `altitude sai inteira em metros`() {
        assertEquals("10 400", FlightFormatting.altitudeMeters(10_400.4, pt).normalizeSpaces())
        assertEquals("950", FlightFormatting.altitudeMeters(949.6, pt))
    }

    @Test
    fun `velocidade converte de metros por segundo para quilometros por hora`() {
        // 233,33 m/s = 840 km/h: a fonte reporta SI e o utilizador lê km/h.
        assertEquals("840", FlightFormatting.speedKmh(233.333, pt))
        assertEquals("0", FlightFormatting.speedKmh(0.0, pt))
    }

    @Test
    fun `elevacao sai inteira em graus`() {
        assertEquals("47", FlightFormatting.elevationDegrees(47.4, pt))
        assertEquals("90", FlightFormatting.elevationDegrees(89.7, pt))
    }

    @Test
    fun `os dezasseis rumos cobrem a rosa dos ventos`() {
        assertEquals(CompassPoint.N, FlightFormatting.compassPointOf(0.0))
        assertEquals(CompassPoint.NNE, FlightFormatting.compassPointOf(22.5))
        assertEquals(CompassPoint.NE, FlightFormatting.compassPointOf(45.0))
        assertEquals(CompassPoint.E, FlightFormatting.compassPointOf(90.0))
        assertEquals(CompassPoint.SE, FlightFormatting.compassPointOf(135.0))
        assertEquals(CompassPoint.S, FlightFormatting.compassPointOf(180.0))
        assertEquals(CompassPoint.SO, FlightFormatting.compassPointOf(225.0))
        assertEquals(CompassPoint.O, FlightFormatting.compassPointOf(270.0))
        assertEquals(CompassPoint.NO, FlightFormatting.compassPointOf(315.0))
        assertEquals(CompassPoint.NNO, FlightFormatting.compassPointOf(337.5))
    }

    @Test
    fun `rumo arredonda para o ponto mais proximo`() {
        assertEquals(CompassPoint.N, FlightFormatting.compassPointOf(11.0))
        assertEquals(CompassPoint.NNE, FlightFormatting.compassPointOf(12.0))
    }

    @Test
    fun `rumo dobra corretamente junto ao norte`() {
        // 350° está mais perto de N do que de NNO; 360° e 0° são o mesmo rumo.
        assertEquals(CompassPoint.N, FlightFormatting.compassPointOf(350.0))
        assertEquals(CompassPoint.N, FlightFormatting.compassPointOf(360.0))
        assertEquals(CompassPoint.N, FlightFormatting.compassPointOf(-1.0))
    }

    @Test
    fun `no zenite o rumo deixa de ter significado`() {
        assertNull(FlightFormatting.compassPointOrNull(bearingDegrees = 45.0, elevationDegrees = 85.0))
        assertNull(FlightFormatting.compassPointOrNull(bearingDegrees = 45.0, elevationDegrees = 90.0))
    }

    @Test
    fun `abaixo do zenite o rumo continua a ser apresentado`() {
        assertNotNull(
            FlightFormatting.compassPointOrNull(bearingDegrees = 45.0, elevationDegrees = 84.9),
        )
    }

    @Test
    fun `idade dos dados sai em segundos ate ao minuto`() {
        assertEquals(Freshness.Seconds(12), FlightFormatting.freshnessOf(112L, 100L))
        assertEquals(Freshness.Seconds(59), FlightFormatting.freshnessOf(159L, 100L))
    }

    @Test
    fun `idade dos dados sai em minutos a partir do minuto`() {
        assertEquals(Freshness.Minutes(1), FlightFormatting.freshnessOf(160L, 100L))
        assertEquals(Freshness.Minutes(2), FlightFormatting.freshnessOf(220L, 100L))
    }

    @Test
    fun `acabado de atualizar nao mostra contagem`() {
        assertEquals(Freshness.JustNow, FlightFormatting.freshnessOf(100L, 100L))
        assertEquals(Freshness.JustNow, FlightFormatting.freshnessOf(104L, 100L))
    }

    @Test
    fun `instante no futuro nao produz idade negativa`() {
        // Acontece quando o relógio do dispositivo está atrás do da fonte.
        assertEquals(Freshness.JustNow, FlightFormatting.freshnessOf(90L, 100L))
    }
}
