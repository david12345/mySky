package com.mysky.app.data

import com.mysky.app.data.mapper.toDomain
import com.mysky.app.data.source.opensky.OpenSkyStateVectorDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSkyMapperTest {

    private fun dto(
        icao24: String = "3c6444",
        callsign: String? = "TAP1234 ",
        originCountry: String? = "Portugal",
        longitude: Double? = -9.1393,
        latitude: Double? = 38.7223,
        barometricAltitudeMeters: Double? = 10_000.0,
        geometricAltitudeMeters: Double? = 10_400.0,
        onGround: Boolean = false,
        velocityMetersPerSecond: Double? = 233.0,
        trueTrackDegrees: Double? = 180.0,
        verticalRateMetersPerSecond: Double? = 0.0,
        lastContactEpochSeconds: Long? = 1_757_000_000L,
    ) = OpenSkyStateVectorDto(
        icao24 = icao24,
        callsign = callsign,
        originCountry = originCountry,
        longitude = longitude,
        latitude = latitude,
        barometricAltitudeMeters = barometricAltitudeMeters,
        geometricAltitudeMeters = geometricAltitudeMeters,
        onGround = onGround,
        velocityMetersPerSecond = velocityMetersPerSecond,
        trueTrackDegrees = trueTrackDegrees,
        verticalRateMetersPerSecond = verticalRateMetersPerSecond,
        lastContactEpochSeconds = lastContactEpochSeconds,
    )

    @Test
    fun `vetor valido converte para aeronave de dominio`() {
        val aircraft = dto().toDomain()!!

        assertEquals("3c6444", aircraft.icao24)
        assertEquals("TAP1234", aircraft.callsign)
        assertEquals(38.7223, aircraft.position!!.latitudeDegrees, 0.00001)
        assertEquals(-9.1393, aircraft.position!!.longitudeDegrees, 0.00001)
        assertEquals(233.0, aircraft.groundSpeedMetersPerSecond!!, 0.001)
    }

    @Test
    fun `indicativo perde os espacos de padding da fonte`() {
        assertEquals("TAP1234", dto(callsign = "  TAP1234  ").toDomain()!!.callsign)
    }

    @Test
    fun `indicativo so com espacos conta como ausente`() {
        assertNull(dto(callsign = "     ").toDomain()!!.callsign)
        assertNull(dto(callsign = "").toDomain()!!.callsign)
        assertNull(dto(callsign = null).toDomain()!!.callsign)
    }

    @Test
    fun `icao24 em branco descarta o registo`() {
        assertNull(dto(icao24 = "").toDomain())
        assertNull(dto(icao24 = "   ").toDomain())
    }

    @Test
    fun `coordenadas fora de intervalo descartam o registo sem lancar`() {
        // Sem esta guarda o `require` de GeoPosition rebentaria e levaria consigo a resposta toda.
        assertNull(dto(latitude = 91.0).toDomain())
        assertNull(dto(latitude = -90.5).toDomain())
        assertNull(dto(longitude = 180.1).toDomain())
        assertNull(dto(longitude = -181.0).toDomain())
    }

    @Test
    fun `coordenadas nos limites exatos sao validas`() {
        assertNotNull(dto(latitude = 90.0, longitude = 180.0).toDomain()!!.position)
        assertNotNull(dto(latitude = -90.0, longitude = -180.0).toDomain()!!.position)
    }

    @Test
    fun `aeronave sem coordenadas sobrevive ao mapeamento sem posicao`() {
        // Descartar aqui seria decidir no sítio errado: quem filtra por posição é a deteção.
        val aircraft = dto(latitude = null, longitude = null).toDomain()

        assertNotNull(aircraft)
        assertNull(aircraft!!.position)
    }

    @Test
    fun `altitude geometrica e preferida a barometrica`() {
        val aircraft = dto(barometricAltitudeMeters = 10_000.0, geometricAltitudeMeters = 10_400.0)
            .toDomain()!!

        assertEquals(10_400.0, aircraft.altitudeMeters!!, 0.001)
    }

    @Test
    fun `sem altitude geometrica usa a barometrica`() {
        val aircraft = dto(barometricAltitudeMeters = 9_800.0, geometricAltitudeMeters = null)
            .toDomain()!!

        assertEquals(9_800.0, aircraft.altitudeMeters!!, 0.001)
    }

    @Test
    fun `icao24 e normalizado para minusculas`() {
        assertEquals("3c6444", dto(icao24 = "3C6444").toDomain()!!.icao24)
    }
}
