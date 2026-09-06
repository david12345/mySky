package com.mysky.app.data

import com.mysky.app.data.source.opensky.OpenSkyStateVector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes de contrato do vetor de estado da OpenSky. O formato não é nosso: só se pode verificar
 * que a leitura sobrevive a tudo o que a fonte já fez ou pode vir a fazer.
 */
class OpenSkyStateVectorTest {

    private fun stateVector(json: String): JsonArray = Json.parseToJsonElement(json).jsonArray

    /** Vetor completo e realista, com os 17 campos que a API documenta. */
    private val complete = stateVector(
        """
        ["3c6444","TAP1234 ","Portugal",1757000000,1757000005,
         -9.1393,38.7223,10000.5,false,233.4,180.2,0.0,null,10400.75,"1000",false,0]
        """,
    )

    @Test
    fun `vetor completo le todos os campos`() {
        val dto = OpenSkyStateVector.parse(complete)

        assertEquals("3c6444", dto.icao24)
        assertEquals("TAP1234 ", dto.callsign)
        assertEquals("Portugal", dto.originCountry)
        assertEquals(1_757_000_005L, dto.lastContactEpochSeconds)
        assertEquals(10_000.5, dto.barometricAltitudeMeters!!, 0.001)
        assertEquals(10_400.75, dto.geometricAltitudeMeters!!, 0.001)
        assertFalse(dto.onGround)
        assertEquals(233.4, dto.velocityMetersPerSecond!!, 0.001)
        assertEquals(180.2, dto.trueTrackDegrees!!, 0.001)
        assertEquals(0.0, dto.verticalRateMetersPerSecond!!, 0.001)
    }

    @Test
    fun `longitude vem no indice 5 e latitude no 6`() {
        // A troca mais fácil de fazer sem dar por ela: as duas são Double e ambas plausíveis.
        // Lisboa tem longitude negativa e latitude positiva, por isso um erro aqui é visível.
        val dto = OpenSkyStateVector.parse(complete)

        assertEquals(-9.1393, dto.longitude!!, 0.00001)
        assertEquals(38.7223, dto.latitude!!, 0.00001)
    }

    @Test
    fun `array mais curto do que o esperado le os campos em falta como ausentes`() {
        val dto = OpenSkyStateVector.parse(stateVector("""["3c6444","TAP1234",null,null,null]"""))

        assertEquals("3c6444", dto.icao24)
        assertEquals("TAP1234", dto.callsign)
        assertNull(dto.longitude)
        assertNull(dto.latitude)
        assertNull(dto.geometricAltitudeMeters)
        assertFalse(dto.onGround)
    }

    @Test
    fun `array vazio nao rebenta`() {
        val dto = OpenSkyStateVector.parse(stateVector("[]"))

        assertEquals("", dto.icao24)
        assertNull(dto.callsign)
        assertFalse(dto.onGround)
    }

    @Test
    fun `null em qualquer posicao opcional le como ausente`() {
        val dto = OpenSkyStateVector.parse(
            stateVector(
                """["3c6444",null,null,null,null,null,null,null,false,null,null,null,null,null]""",
            ),
        )

        assertEquals("3c6444", dto.icao24)
        assertNull(dto.callsign)
        assertNull(dto.originCountry)
        assertNull(dto.longitude)
        assertNull(dto.latitude)
        assertNull(dto.barometricAltitudeMeters)
        assertNull(dto.geometricAltitudeMeters)
        assertNull(dto.velocityMetersPerSecond)
        assertNull(dto.trueTrackDegrees)
        assertNull(dto.verticalRateMetersPerSecond)
        assertNull(dto.lastContactEpochSeconds)
    }

    @Test
    fun `campos novos no fim do array sao ignorados`() {
        // A API já cresceu antes (position_source foi acrescentado depois do lançamento).
        val grown = stateVector(
            """
            ["3c6444","TAP1234","Portugal",1757000000,1757000005,
             -9.1393,38.7223,10000.5,false,233.4,180.2,0.0,null,10400.75,"1000",false,0,
             "campo novo",123,{"objeto":"inesperado"}]
            """,
        )

        val dto = OpenSkyStateVector.parse(grown)

        assertEquals("3c6444", dto.icao24)
        assertEquals(10_400.75, dto.geometricAltitudeMeters!!, 0.001)
    }

    @Test
    fun `indicativo com espacos a direita chega intacto ao DTO`() {
        // O trim é responsabilidade do mapeamento, não da leitura posicional: aqui só se garante
        // que os espaços não são perdidos nem confundidos com ausência.
        val dto = OpenSkyStateVector.parse(stateVector("""["3c6444","TAP1234   "]"""))

        assertEquals("TAP1234   ", dto.callsign)
    }

    @Test
    fun `aeronave em solo le onGround verdadeiro`() {
        val dto = OpenSkyStateVector.parse(
            stateVector("""["3c6444","TAP1234","Portugal",null,null,-9.1,38.7,null,true]"""),
        )

        assertEquals(true, dto.onGround)
    }
}
