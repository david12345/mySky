package com.mysky.app.presentation.format

import androidx.annotation.StringRes
import com.mysky.app.R
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Conversão das grandezas do domínio (SI) para o que o utilizador lê.
 *
 * Devolve **números já formatados e tipos**, nunca frases montadas: a unidade e a pontuação vivem
 * nos recursos de string, e é lá que ficam quando a app for traduzida. É também o que mantém estas
 * funções puras e testáveis na JVM sem um `Context`.
 *
 * Quilómetros, metros e km/h são fixos nesta feature; a escolha de unidades pertence à feature de
 * definições (FR-010, FR-015).
 */
object FlightFormatting {

    private const val METERS_PER_KILOMETER = 1_000.0
    private const val SECONDS_PER_HOUR = 3_600.0
    private const val DEGREES_PER_COMPASS_POINT = 360.0 / 16

    /** Acima disto a aeronave está tão perto do zénite que um rumo deixaria de querer dizer algo. */
    const val OVERHEAD_ELEVATION_DEGREES = 85.0

    /** Abaixo desta razão vertical, em m/s, o voo apresenta-se como nivelado (~100 ft/min). */
    const val LEVEL_FLIGHT_THRESHOLD = 0.5

    fun distanceKm(meters: Double, locale: Locale = Locale.getDefault()): String =
        decimalFormat(locale).format(meters / METERS_PER_KILOMETER)

    fun altitudeMeters(meters: Double, locale: Locale = Locale.getDefault()): String =
        integerFormat(locale).format(meters)

    fun speedKmh(metersPerSecond: Double, locale: Locale = Locale.getDefault()): String =
        integerFormat(locale).format(metersPerSecond * SECONDS_PER_HOUR / METERS_PER_KILOMETER)

    fun elevationDegrees(degrees: Double, locale: Locale = Locale.getDefault()): String =
        integerFormat(locale).format(degrees)

    /**
     * Razão de subida ou descida, reduzida ao que o utilizador precisa de saber.
     *
     * Abaixo de [LEVEL_FLIGHT_THRESHOLD] o voo é **nivelado**: são cerca de 100 ft/min, o limiar
     * convencional para considerar um voo estabilizado, e abaixo disso o ruído do vetor de estado é
     * da ordem do valor medido. Um avião a 0,4 m/s ganha 24 metros num minuto — dizer "a subir"
     * seria verdade aritmética e mentira prática.
     *
     * As três variantes existem para a UI não poder esquecer-se do caso nivelado e o deixar sair
     * como "a subir 0 m/s".
     */
    fun verticalMovementOf(metersPerSecond: Double): VerticalMovement = when {
        metersPerSecond >= LEVEL_FLIGHT_THRESHOLD -> VerticalMovement.Climbing(metersPerSecond)
        metersPerSecond <= -LEVEL_FLIGHT_THRESHOLD -> VerticalMovement.Descending(-metersPerSecond)
        else -> VerticalMovement.Level
    }

    /** Magnitude da subida ou descida, já sem sinal: o sentido vem da variante, não do número. */
    fun verticalRateMetersPerSecond(metersPerSecond: Double, locale: Locale = Locale.getDefault()): String =
        decimalFormat(locale).format(metersPerSecond)

    /** `null` no zénite: quem apresenta mostra "mesmo por cima" em vez de um rumo (FR-014). */
    fun compassPointOrNull(bearingDegrees: Double, elevationDegrees: Double): CompassPoint? =
        if (elevationDegrees >= OVERHEAD_ELEVATION_DEGREES) null else compassPointOf(bearingDegrees)

    /**
     * Ponto cardinal de um ângulo qualquer.
     *
     * Serve os **dois** ângulos que o detalhe apresenta, e que são coisas diferentes: o rumo da
     * aeronave (para onde ela vai) e o azimute do observador para ela (para onde olhar). Só o
     * segundo deixa de ter significado no zénite — para esse existe [compassPointOrNull]. Suprimir
     * o rumo de um avião que passa mesmo por cima seria apagar informação correta.
     */
    fun compassPointOf(bearingDegrees: Double): CompassPoint {
        val normalized = ((bearingDegrees % 360) + 360) % 360
        val index = Math.round(normalized / DEGREES_PER_COMPASS_POINT).toInt() % CompassPoint.entries.size
        return CompassPoint.entries[index]
    }

    /**
     * Há quanto tempo os dados foram obtidos (FR-018).
     *
     * Um instante no futuro — relógio do dispositivo à frente do da fonte — é tratado como "agora",
     * porque "há -3 s" só serviria para o utilizador desconfiar da app toda.
     */
    fun freshnessOf(nowEpochSeconds: Long, updatedEpochSeconds: Long): Freshness {
        val ageSeconds = (nowEpochSeconds - updatedEpochSeconds).coerceAtLeast(0)
        return when {
            ageSeconds < JUST_NOW_SECONDS -> Freshness.JustNow
            ageSeconds < SECONDS_PER_MINUTE -> Freshness.Seconds(ageSeconds.toInt())
            else -> Freshness.Minutes((ageSeconds / SECONDS_PER_MINUTE).toInt())
        }
    }

    private const val JUST_NOW_SECONDS = 5L
    private const val SECONDS_PER_MINUTE = 60L

    private fun decimalFormat(locale: Locale) = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }

    private fun integerFormat(locale: Locale) = NumberFormat.getIntegerInstance(locale).apply {
        roundingMode = RoundingMode.HALF_UP
    }
}

/** Os 16 rumos da rosa dos ventos, em português (O de oeste, não W). */
enum class CompassPoint(@param:StringRes val labelRes: Int) {
    N(R.string.bearing_n),
    NNE(R.string.bearing_nne),
    NE(R.string.bearing_ne),
    ENE(R.string.bearing_ene),
    E(R.string.bearing_e),
    ESE(R.string.bearing_ese),
    SE(R.string.bearing_se),
    SSE(R.string.bearing_sse),
    S(R.string.bearing_s),
    SSO(R.string.bearing_sso),
    SO(R.string.bearing_so),
    OSO(R.string.bearing_oso),
    O(R.string.bearing_o),
    ONO(R.string.bearing_ono),
    NO(R.string.bearing_no),
    NNO(R.string.bearing_nno),
}

/**
 * Sentido do movimento vertical da aeronave.
 *
 * [Climbing] e [Descending] transportam a magnitude **sem sinal**: o sentido está na variante, e o
 * número que o utilizador lê nunca aparece negativo.
 */
sealed interface VerticalMovement {
    data class Climbing(val metersPerSecond: Double) : VerticalMovement
    data class Descending(val metersPerSecond: Double) : VerticalMovement
    data object Level : VerticalMovement
}

/** Idade dos dados apresentados, já reduzida à unidade que a UI mostra. */
sealed interface Freshness {
    data object JustNow : Freshness
    data class Seconds(val value: Int) : Freshness
    data class Minutes(val value: Int) : Freshness
}
