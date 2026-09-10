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
 * interagem**. Com o ângulo mínimo em 25°, aumentar o raio para 150 km só traz aeronaves para serem
 * deitadas fora pelo filtro de elevação.
 */
object SkyRange {

    /**
     * Altitude de cruzeiro do tráfego civil mais alto, em metros.
     *
     * **Porque é o teto e não a altitude típica** — que é a pergunta que este ficheiro tem de
     * responder, porque a escolha errada aqui produz uma mentira com ar de verdade:
     *
     * A frase que o aviso mostra ao utilizador é uma afirmação **universal** — "aumentar o raio além
     * disso não traz aviões novos". Uma afirmação sobre *nenhum* avião só é verdadeira se for
     * verificada no caso mais favorável a haver um. Medida contra uma altitude típica (12 km), o
     * aviso dispararia a 26 km e diria "não traz aviões novos" enquanto o tráfego a 14 km continuava
     * a aparecer até aos 30 km — correto na aritmética, falso no céu. Medida contra o teto, quando o
     * aviso aparece nenhum tráfego civil clareia o ângulo àquela distância, e a frase é verdadeira
     * para todos.
     *
     * 14 km são cerca de 46 000 pés: acima do tráfego de linha e no topo do executivo.
     *
     * É também o teto de que `data-model.md` deriva o raio máximo de 150 km — as duas contas do
     * projeto passam a assumir a mesma coisa. Assumiam 14 km e 12 km, e essa divergência fazia o
     * aviso aparecer com os valores de fábrica, sem o utilizador ter tocado em nada.
     */
    const val HIGH_CRUISE_ALTITUDE_METERS = 14_000.0

    /**
     * Distância horizontal, em metros, além da qual uma aeronave a [altitudeMeters] deixa de estar
     * acima de [minElevationDegrees].
     *
     * @return `Double.POSITIVE_INFINITY` para um ângulo de zero ou negativo — a partir do horizonte
     *   não há limite, e devolver um número finito seria inventar um.
     */
    fun usefulRangeMeters(
        minElevationDegrees: Double,
        altitudeMeters: Double = HIGH_CRUISE_ALTITUDE_METERS,
    ): Double {
        if (minElevationDegrees <= 0.0) return Double.POSITIVE_INFINITY
        if (minElevationDegrees >= 90.0) return 0.0
        return altitudeMeters / tan(Math.toRadians(minElevationDegrees))
    }

    /**
     * O raio escolhido vai além do que o ângulo torna visível?
     *
     * Quando sim, aumentar o raio não traz aeronaves novas — traz as mesmas, e mais algumas para
     * serem descartadas pelo filtro de elevação. Ver [HIGH_CRUISE_ALTITUDE_METERS] para a razão de a
     * conta usar o teto de altitude e não a altitude típica.
     *
     * Continua a ser uma aproximação: assume que a aeronave está em cruzeiro. Por isso informa
     * (FR-014) e nunca impede uma escolha (AD-021).
     */
    fun radiusExceedsUsefulRange(
        radiusMeters: Double,
        minElevationDegrees: Double,
        altitudeMeters: Double = HIGH_CRUISE_ALTITUDE_METERS,
    ): Boolean = radiusMeters > usefulRangeMeters(minElevationDegrees, altitudeMeters)
}
