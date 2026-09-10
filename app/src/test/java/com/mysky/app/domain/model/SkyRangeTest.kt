package com.mysky.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O alcance útil, que é a inversa da geometria que já existe.
 *
 * Os valores esperados são os da tabela de `data-model.md`, e existem para que a frase que o
 * utilizador lê no ecrã não possa divergir da conta que a produziu.
 */
class SkyRangeTest {

    @Test
    fun `o alcance util a vinte e cinco graus e cerca de vinte e seis quilometros`() {
        // É o valor de origem, e o que explica por que razão o raio de 30 km já é generoso.
        assertEquals(25_700.0, SkyRange.usefulRangeMeters(25.0), 200.0)
    }

    @Test
    fun `baixar o angulo alarga muito o alcance util`() {
        assertEquals(137_200.0, SkyRange.usefulRangeMeters(5.0), 500.0)
        assertEquals(44_800.0, SkyRange.usefulRangeMeters(15.0), 300.0)
    }

    @Test
    fun `subir o angulo encurta-o depressa`() {
        assertEquals(12_000.0, SkyRange.usefulRangeMeters(45.0), 100.0)
        assertEquals(6_900.0, SkyRange.usefulRangeMeters(60.0), 100.0)
    }

    @Test
    fun `um angulo de zero nao tem alcance limite`() {
        // A partir do horizonte não há distância a que a aeronave deixe de estar acima dele.
        // Devolver um número finito seria inventar um limite que a geometria não impõe.
        assertEquals(Double.POSITIVE_INFINITY, SkyRange.usefulRangeMeters(0.0), 0.0)
        assertEquals(Double.POSITIVE_INFINITY, SkyRange.usefulRangeMeters(-5.0), 0.0)
    }

    @Test
    fun `no zenite o alcance util e zero`() {
        assertEquals(0.0, SkyRange.usefulRangeMeters(90.0), 0.001)
    }

    @Test
    fun `uma altitude maior alarga o alcance util`() {
        // A conta é uma aproximação sobre um teto assumido; tráfego mais alto vê-se de mais longe.
        val comercial = SkyRange.usefulRangeMeters(25.0, altitudeMeters = 12_000.0)
        val executivo = SkyRange.usefulRangeMeters(25.0, altitudeMeters = 14_000.0)

        assertTrue("$executivo devia ser maior que $comercial", executivo > comercial)
    }

    // --- A incoerência que o ecrã tem de avisar -------------------------------------------------

    @Test
    fun `o raio de origem cabe no alcance util do angulo de origem`() {
        // 30 km contra ~26 km: ligeiramente generoso, e é o comportamento de hoje. Não deve avisar.
        assertFalse(SkyRange.radiusExceedsUsefulRange(radiusMeters = 25_000.0, minElevationDegrees = 25.0))
    }

    @Test
    fun `um raio grande com o angulo de origem excede o alcance util`() {
        // O caso que o utilizador não tem como descobrir sozinho: pôr o raio no máximo com 25° não
        // traz aeronave nenhuma nova.
        assertTrue(SkyRange.radiusExceedsUsefulRange(radiusMeters = 150_000.0, minElevationDegrees = 25.0))
    }

    @Test
    fun `com o angulo no minimo um raio grande deixa de ser excessivo`() {
        assertFalse(SkyRange.radiusExceedsUsefulRange(radiusMeters = 130_000.0, minElevationDegrees = 5.0))
    }
}
