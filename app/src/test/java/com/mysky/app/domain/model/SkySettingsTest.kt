package com.mysky.app.domain.model

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
        // O intervalo do trabalho periódico é do widget; as notificações e o widget são das suas
        // features. Estão na mesma classe, o que os torna fáceis de arrastar por engano (FR-015).
        val doutrasFeatures = defaults.copy(
            refreshIntervalMinutes = 45L,
            notificationsEnabled = true,
            widgetEnabled = false,
            detectionRadiusMeters = 99_000.0,
        )

        val reposto = doutrasFeatures.withDefaults()

        assertEquals(45L, reposto.refreshIntervalMinutes)
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
        )

        assertEquals(minimos, minimos.coerced())
        assertEquals(maximos, maximos.coerced())
    }
}
