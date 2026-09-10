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
    fun `o alcance util a vinte e cinco graus e cerca de trinta quilometros`() {
        // É o ângulo de origem, e explica por que razão o raio de origem é de 30 km: é exatamente o
        // ponto onde o tráfego mais alto deixa de clarear 25°.
        assertEquals(30_000.0, SkyRange.usefulRangeMeters(25.0), 200.0)
    }

    @Test
    fun `baixar o angulo alarga muito o alcance util`() {
        assertEquals(160_000.0, SkyRange.usefulRangeMeters(5.0), 500.0)
        assertEquals(52_200.0, SkyRange.usefulRangeMeters(15.0), 300.0)
    }

    @Test
    fun `subir o angulo encurta-o depressa`() {
        assertEquals(14_000.0, SkyRange.usefulRangeMeters(45.0), 100.0)
        assertEquals(8_100.0, SkyRange.usefulRangeMeters(60.0), 100.0)
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

    @Test
    fun `a conta assume o teto de altitude e nao a altitude tipica`() {
        // O aviso afirma que **nenhum** avião novo aparece. Medido contra os 12 km típicos, o alcance
        // dava ~26 km e a afirmação era falsa para o tráfego a 14 km, que se vê até aos 30 km.
        assertEquals(14_000.0, SkyRange.HIGH_CRUISE_ALTITUDE_METERS, 0.0)
        assertEquals(
            SkyRange.usefulRangeMeters(25.0, altitudeMeters = SkyRange.HIGH_CRUISE_ALTITUDE_METERS),
            SkyRange.usefulRangeMeters(25.0),
            0.001,
        )
    }

    // --- A incoerência que o ecrã tem de avisar -------------------------------------------------

    @Test
    fun `os valores de origem nunca acendem o aviso`() {
        // O teste que faltava, e que estava escrito de forma a não poder falhar: o nome dizia "raio de
        // origem" e passava 25 000, quando o valor de origem são 30 000. Com o teto errado (12 km) o
        // aviso aparecia a **todos** os utilizadores no primeiro arranque, sobre uma escolha que nunca
        // fizeram.
        //
        // Os valores vêm de `SkySettings()` e não escritos à mão: se algum deles mudar, é este teste
        // que tem de ser olhado outra vez, em vez de continuar a passar sobre números que já ninguém
        // usa.
        val origem = SkySettings()

        assertFalse(
            "os valores de fábrica não podem avisar contra si mesmos",
            SkyRange.radiusExceedsUsefulRange(
                radiusMeters = origem.detectionRadiusMeters,
                minElevationDegrees = origem.minElevationDegrees,
            ),
        )
    }

    @Test
    fun `o raio de origem esta no limite do alcance util e nao alem dele`() {
        // Explicita a folga real, que é pequena: 30 000 contra 30 023. Não é coincidência — o raio de
        // origem foi escolhido como o alcance útil do ângulo de origem. Fica escrito para que uma
        // alteração a qualquer um dos dois valores apareça aqui.
        val origem = SkySettings()
        val alcance = SkyRange.usefulRangeMeters(origem.minElevationDegrees)

        assertTrue(
            "o raio de origem ($origem.detectionRadiusMeters) devia caber em $alcance",
            origem.detectionRadiusMeters <= alcance,
        )
        assertEquals(30_023.0, alcance, 1.0)
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
