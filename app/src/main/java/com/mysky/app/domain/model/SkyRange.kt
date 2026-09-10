package com.mysky.app.domain.model

import kotlin.math.tan

/**
 * Até onde vale a pena procurar, dado o ângulo mínimo escolhido.
 *
 * É a **inversa** do que o `GeoCalculator` já faz — ele calcula o ângulo de elevação a partir da
 * altitude e da distância, e isto devolve a distância a partir do ângulo e da altitude. Não há
 * geometria nova; há a mesma relação lida do outro lado.
 *
 * Serve para o utilizador descobrir uma coisa que nenhum ecrã lhe diria: **as duas definições
 * interagem**. Com o ângulo mínimo em 25°, uma aeronave a 12 km de altitude deixa de ser visível
 * além de ~26 km — e aumentar o raio para 150 km só traz aeronaves para serem deitadas fora.
 *
 * **É uma aproximação, e tem de ser lida como tal.** Assume um teto de altitude típico de tráfego
 * comercial. Há tráfego executivo que voa bem mais alto, e para esse a conta dá outro resultado. Por
 * isso informa (FR-014) e nunca impede uma escolha (AD-021).
 */
object SkyRange {

    /**
     * Altitude típica de cruzeiro do tráfego comercial, em metros.
     *
     * Escolhida como referência para a conta, não como limite de nada. 12 km são cerca de 39 000
     * pés — o meio da gama onde a maioria dos voos de linha viaja.
     */
    const val TYPICAL_CRUISE_ALTITUDE_METERS = 12_000.0

    /**
     * Distância horizontal, em metros, além da qual uma aeronave a [altitudeMeters] deixa de estar
     * acima de [minElevationDegrees].
     *
     * @return `Double.POSITIVE_INFINITY` para um ângulo de zero ou negativo — a partir do horizonte
     *   não há limite, e devolver um número finito seria inventar um.
     */
    fun usefulRangeMeters(
        minElevationDegrees: Double,
        altitudeMeters: Double = TYPICAL_CRUISE_ALTITUDE_METERS,
    ): Double {
        if (minElevationDegrees <= 0.0) return Double.POSITIVE_INFINITY
        if (minElevationDegrees >= 90.0) return 0.0
        return altitudeMeters / tan(Math.toRadians(minElevationDegrees))
    }

    /**
     * O raio escolhido vai além do que o ângulo torna visível?
     *
     * Quando sim, aumentar o raio não traz aeronaves novas — traz as mesmas, e mais algumas para
     * serem descartadas pelo filtro de elevação.
     */
    fun radiusExceedsUsefulRange(
        radiusMeters: Double,
        minElevationDegrees: Double,
        altitudeMeters: Double = TYPICAL_CRUISE_ALTITUDE_METERS,
    ): Boolean = radiusMeters > usefulRangeMeters(minElevationDegrees, altitudeMeters)
}
