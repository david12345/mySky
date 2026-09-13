package com.mysky.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Os limites e a degradação.
 *
 * O teste que interessa mais não é o que verifica a correção — é o que verifica que um valor
 * **legítimo passa intacto**. Um limite mal escrito aqui não dá erro nenhum: limita-se a "corrigir"
 * silenciosamente escolhas que o utilizador fez de propósito, e ele nunca sabe porquê.
 */
class SkySettingsTest {

    private val defaults = SkySettings()

    @Test
    fun `os valores de origem ja estao dentro dos limites`() {
        // Se isto falhar, a app arranca a corrigir-se a si mesma — e os valores que a 001 fixou no
        // código deixariam de ser alcançáveis pelo utilizador.
        assertEquals(defaults, defaults.coerced())
    }

    @Test
    fun `um valor dentro dos limites passa intacto`() {
        val escolhido = defaults.copy(
            detectionRadiusMeters = 80_000.0,
            minElevationDegrees = 12.0,
            minAltitudeMeters = 1_500.0,
        )

        assertEquals(escolhido, escolhido.coerced())
    }

    @Test
    fun `um raio abaixo do minimo sobe para o minimo`() {
        assertEquals(5_000.0, defaults.copy(detectionRadiusMeters = 100.0).coerced().detectionRadiusMeters, 0.001)
    }

    @Test
    fun `um raio acima do maximo desce para o maximo`() {
        assertEquals(150_000.0, defaults.copy(detectionRadiusMeters = 900_000.0).coerced().detectionRadiusMeters, 0.001)
    }

    @Test
    fun `um angulo fora dos limites e corrigido nos dois sentidos`() {
        assertEquals(5.0, defaults.copy(minElevationDegrees = -10.0).coerced().minElevationDegrees, 0.001)
        assertEquals(60.0, defaults.copy(minElevationDegrees = 89.0).coerced().minElevationDegrees, 0.001)
    }

    @Test
    fun `uma altitude fora dos limites e corrigida nos dois sentidos`() {
        assertEquals(0.0, defaults.copy(minAltitudeMeters = -500.0).coerced().minAltitudeMeters, 0.001)
        assertEquals(3_000.0, defaults.copy(minAltitudeMeters = 20_000.0).coerced().minAltitudeMeters, 0.001)
    }

    @Test
    fun `o intervalo do trabalho periodico nunca desce abaixo do minimo do Android`() {
        // Não é campo desta feature, mas é o único que uma escrita distraída poderia levar abaixo de
        // um limite imposto pela plataforma.
        assertEquals(15L, defaults.copy(refreshIntervalMinutes = 1L).coerced().refreshIntervalMinutes)
    }

    // --- A reposição toca no que é desta feature, e em mais nada ---------------------------------

    @Test
    fun `repor devolve os criterios e as unidades aos valores de origem`() {
        val mexido = defaults.copy(
            detectionRadiusMeters = 120_000.0,
            minElevationDegrees = 8.0,
            minAltitudeMeters = 2_000.0,
            distanceUnit = DistanceUnit.MILES,
            altitudeUnit = AltitudeUnit.FEET,
        )

        assertEquals(defaults, mexido.withDefaults())
    }

    @Test
    fun `repor nao toca no que pertence a outras features`() {
        // **Alterado na 005.** Este teste afirmava que `refreshIntervalMinutes` sobrevivia ao repor, e
        // estava certo enquanto esse campo não tinha controlo em lado nenhum: repor um valor que o
        // utilizador nunca pôde escolher não fazia sentido. A 005 deu-lhe um cursor no mesmo ecrã, e a
        // regra de `withDefaults` — "os campos ajustáveis no ecrã" — passou a abrangê-lo. Deixá-lo de
        // fora faria o botão repor tudo menos uma coisa, sem o utilizador ter como saber qual.
        //
        // `notificationsEnabled` e `widgetEnabled` continuam de fora porque continuam sem controlo.
        val doutrasFeatures = defaults.copy(
            refreshIntervalMinutes = 45L,
            notificationsEnabled = true,
            widgetEnabled = false,
            detectionRadiusMeters = 99_000.0,
        )

        val reposto = doutrasFeatures.withDefaults()

        assertEquals(SkySettings.DEFAULT_REFRESH_INTERVAL_MINUTES, reposto.refreshIntervalMinutes)
        assertEquals(true, reposto.notificationsEnabled)
        assertEquals(false, reposto.widgetEnabled)
        assertEquals(SkySettings().detectionRadiusMeters, reposto.detectionRadiusMeters, 0.001)
    }

    @Test
    fun `os criterios saem do modelo tal como foram escolhidos`() {
        val escolhido = defaults.copy(
            detectionRadiusMeters = 50_000.0,
            minElevationDegrees = 10.0,
            minAltitudeMeters = 500.0,
        )

        val criteria = escolhido.toCriteria()

        assertEquals(50_000.0, criteria.maxHorizontalDistanceMeters, 0.001)
        assertEquals(10.0, criteria.minElevationDegrees, 0.001)
        assertEquals(500.0, criteria.minAltitudeMeters, 0.001)
    }

    @Test
    fun `os valores exatamente na fronteira passam intactos`() {
        // Os intervalos são inclusivos, e é o que faz o cursor no extremo gravar o extremo em vez de
        // um valor um passo atrás. Testado nas seis fronteiras porque `coerceIn` inclusivo é uma
        // escolha, não uma inevitabilidade: um `coerceIn` exclusivo daria um cursor que nunca chega ao
        // fim do seu próprio trilho.
        val minimos = SkySettings(
            detectionRadiusMeters = SkySettings.RADIUS_RANGE.start,
            minElevationDegrees = SkySettings.MIN_ELEVATION_RANGE.start,
            minAltitudeMeters = SkySettings.MIN_ALTITUDE_RANGE.start,
        )
        val maximos = SkySettings(
            detectionRadiusMeters = SkySettings.RADIUS_RANGE.endInclusive,
            minElevationDegrees = SkySettings.MIN_ELEVATION_RANGE.endInclusive,
            minAltitudeMeters = SkySettings.MIN_ALTITUDE_RANGE.endInclusive,
            // Acrescentado na 006: com o ângulo mínimo no máximo (60°), o limiar de aviso de origem
            // (30°) deixa de ser alcançável e é corrigido para cima. O objeto tem de ser coerente
            // consigo próprio para poder ser comparado com a sua própria correção — foi este teste
            // que apanhou a interação, e é bom sinal que a tenha apanhado.
            notificationThresholdDegrees = SkySettings.MIN_ELEVATION_RANGE.endInclusive,
        )

        assertEquals(minimos, minimos.coerced())
        assertEquals(maximos, maximos.coerced())
    }

    // --- A cadência do trabalho de fundo (005-sky-widget) ---------------------------------------

    @Test
    fun `uma cadencia abaixo do minimo da plataforma sobe para o minimo`() {
        // O mínimo não é preferência nossa: é o que o Android impõe a trabalho periódico. Um valor
        // abaixo dele produziria um agendamento que o sistema silenciosamente reescreve, e a app
        // passaria a mostrar ao utilizador uma cadência que não é a real.
        val abaixo = SkySettings(refreshIntervalMinutes = 5L).coerced()

        assertEquals(SkySettings.MIN_REFRESH_INTERVAL_MINUTES, abaixo.refreshIntervalMinutes)
    }

    @Test
    fun `uma cadencia acima do maximo desce para o maximo`() {
        val acima = SkySettings(refreshIntervalMinutes = 10_000L).coerced()

        assertEquals(SkySettings.REFRESH_INTERVAL_RANGE.last, acima.refreshIntervalMinutes)
    }

    @Test
    fun `as fronteiras da cadencia passam intactas`() {
        val minimo = SkySettings(refreshIntervalMinutes = SkySettings.REFRESH_INTERVAL_RANGE.first)
        val maximo = SkySettings(refreshIntervalMinutes = SkySettings.REFRESH_INTERVAL_RANGE.last)

        assertEquals(minimo, minimo.coerced())
        assertEquals(maximo, maximo.coerced())
    }

    @Test
    fun `repor devolve tambem a cadencia ao valor de origem`() {
        // Mudou na 005. A regra de `withDefaults` sempre foi "os campos ajustáveis no ecrã"; a
        // cadência passou a ser um deles quando ganhou controlo.
        val mexido = SkySettings(refreshIntervalMinutes = 120L)

        assertEquals(SkySettings.DEFAULT_REFRESH_INTERVAL_MINUTES, mexido.withDefaults().refreshIntervalMinutes)
    }

    @Test
    fun `repor continua a nao tocar no que ainda nao tem controlo`() {
        // `notificationsEnabled` pertence à feature seguinte e ainda não é ajustável em lado nenhum.
        val mexido = SkySettings(notificationsEnabled = true, widgetEnabled = false)

        assertTrue(mexido.withDefaults().notificationsEnabled)
        assertFalse(mexido.withDefaults().widgetEnabled)
    }

    // --- O limiar de aviso e o seu piso dependente (006) ----------------------------------------

    @Test
    fun `o limiar de aviso nunca fica abaixo do angulo minimo de detecao`() {
        // Não é uma preferência: abaixo do mínimo de deteção a faixa é **inatingível**, porque essas
        // aeronaves já foram descartadas antes de chegarem à seleção do candidato. Deixá-la escolher
        // daria um cursor com metade do curso sem efeito nenhum.
        val incoerente = SkySettings(minElevationDegrees = 40.0, notificationThresholdDegrees = 10.0)

        assertEquals(40.0, incoerente.coerced().notificationThresholdDegrees, 0.001)
    }

    @Test
    fun `um limiar acima do minimo e respeitado tal como esta`() {
        val coerente = SkySettings(minElevationDegrees = 15.0, notificationThresholdDegrees = 55.0)

        assertEquals(55.0, coerente.coerced().notificationThresholdDegrees, 0.001)
    }

    @Test
    fun `o piso do aviso nunca mexe no intervalo do controlo de detecao`() {
        // A dependência é de um só sentido. Se fosse simétrica, seria o problema que a AD-021
        // rejeitou: um limite a mover-se debaixo do dedo do utilizador.
        val comLimiarAlto = SkySettings(minElevationDegrees = 10.0, notificationThresholdDegrees = 80.0)

        assertEquals(10.0, comLimiarAlto.coerced().minElevationDegrees, 0.001)
    }

    @Test
    fun `as notificacoes estao desligadas de origem`() {
        // FR-001, e é a constituição a exigi-lo. Um valor de origem que interrompa o utilizador sem
        // ele ter pedido seria motivo para desinstalar.
        assertFalse(SkySettings().notificationsEnabled)
    }

    @Test
    fun `o limiar de aviso de origem sao trinta graus`() {
        assertEquals(30.0, SkySettings().notificationThresholdDegrees, 0.0)
    }
}
