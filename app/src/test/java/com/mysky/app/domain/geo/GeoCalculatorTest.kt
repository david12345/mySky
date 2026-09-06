package com.mysky.app.domain.geo

import com.mysky.app.domain.model.GeoPosition
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCalculatorTest {

    private val calculator = GeoCalculator()

    private val lisbon = GeoPosition(38.7223, -9.1393)
    private val porto = GeoPosition(41.1579, -8.6291)

    @Test
    fun `distancia entre o mesmo ponto e zero`() {
        assertEquals(0.0, calculator.distanceMeters(lisbon, lisbon), 0.001)
    }

    @Test
    fun `distancia Lisboa-Porto ronda os 274 km`() {
        val distance = calculator.distanceMeters(lisbon, porto)
        assertTrue("Distância inesperada: $distance", abs(distance - 274_000) < 5_000)
    }

    @Test
    fun `distancia e simetrica`() {
        assertEquals(
            calculator.distanceMeters(lisbon, porto),
            calculator.distanceMeters(porto, lisbon),
            0.001,
        )
    }

    @Test
    fun `distancia atraves do antimeridiano e curta`() {
        val west = GeoPosition(0.0, 179.99)
        val east = GeoPosition(0.0, -179.99)
        val distance = calculator.distanceMeters(west, east)
        assertTrue("Devia ser ~2 km, foi $distance", distance < 3_000)
    }

    @Test
    fun `rumo para norte e zero graus`() {
        val north = GeoPosition(lisbon.latitudeDegrees + 0.1, lisbon.longitudeDegrees)
        assertEquals(0.0, calculator.bearingDegrees(lisbon, north), 0.5)
    }

    @Test
    fun `rumo para este e noventa graus`() {
        val east = GeoPosition(0.0, 0.1)
        assertEquals(90.0, calculator.bearingDegrees(GeoPosition(0.0, 0.0), east), 0.5)
    }

    @Test
    fun `rumo esta sempre normalizado entre zero e 360`() {
        val west = GeoPosition(lisbon.latitudeDegrees, lisbon.longitudeDegrees - 0.1)
        val bearing = calculator.bearingDegrees(lisbon, west)
        assertTrue("Fora de intervalo: $bearing", bearing in 0.0..360.0)
        assertEquals(270.0, bearing, 1.0)
    }

    @Test
    fun `aviao mesmo por cima tem elevacao de 90 graus`() {
        assertEquals(90.0, calculator.elevationDegrees(0.0, 10_000.0), 0.001)
    }

    @Test
    fun `elevacao e 45 graus quando altitude iguala a distancia`() {
        assertEquals(45.0, calculator.elevationDegrees(10_000.0, 10_000.0), 0.001)
    }

    @Test
    fun `altitude nao positiva da elevacao zero`() {
        assertEquals(0.0, calculator.elevationDegrees(1_000.0, 0.0), 0.001)
        assertEquals(0.0, calculator.elevationDegrees(1_000.0, -50.0), 0.001)
    }

    @Test
    fun `caixa normal contem o centro e nao e dividida`() {
        val boxes = calculator.boundingBoxesAround(lisbon, 30_000.0)
        assertEquals(1, boxes.size)
        val box = boxes.single()
        assertTrue(lisbon.latitudeDegrees in box.minLatitude..box.maxLatitude)
        assertTrue(lisbon.longitudeDegrees in box.minLongitude..box.maxLongitude)
    }

    @Test
    fun `caixa sobre o antimeridiano e dividida em duas`() {
        val boxes = calculator.boundingBoxesAround(GeoPosition(0.0, 179.9), 30_000.0)
        assertEquals(2, boxes.size)
        assertTrue(boxes.all { it.minLongitude <= it.maxLongitude })
    }

    @Test
    fun `caixa sobre o polo cobre toda a longitude`() {
        val boxes = calculator.boundingBoxesAround(GeoPosition(89.99, 0.0), 50_000.0)
        val box = boxes.single()
        assertEquals(-180.0, box.minLongitude, 0.001)
        assertEquals(180.0, box.maxLongitude, 0.001)
        assertTrue(box.maxLatitude <= 90.0)
    }
}
