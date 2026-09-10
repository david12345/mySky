package com.mysky.app.domain.geo

import com.mysky.app.domain.model.BoundingBox
import com.mysky.app.domain.model.GeoPosition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometria esférica usada pelo domínio. Kotlin puro, sem dependências de Android:
 * testável na JVM e reutilizável por qualquer camada.
 */
@Singleton
class GeoCalculator @Inject constructor() {

    /**
     * Distância de grande círculo entre dois pontos, em metros (fórmula de Haversine).
     *
     * Erro típico < 0,5% face ao elipsoide WGS84, o que é irrelevante para raios de dezenas de km.
     */
    fun distanceMeters(from: GeoPosition, to: GeoPosition): Double {
        val lat1 = Math.toRadians(from.latitudeDegrees)
        val lat2 = Math.toRadians(to.latitudeDegrees)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(to.longitudeDegrees - from.longitudeDegrees)

        val a = sin(deltaLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
        // asin(sqrt(a)) em vez de atan2: numericamente estável e já lida com o antimeridiano,
        // porque sin/cos são periódicos em deltaLon.
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /**
     * Azimute inicial de [from] para [to], em graus normalizados a [0, 360).
     *
     * Quando os dois pontos coincidem o resultado é 0 (norte) por convenção: nesse caso a aeronave
     * está no zénite e a direção não tem significado — usar o ângulo de elevação para decidir.
     */
    fun bearingDegrees(from: GeoPosition, to: GeoPosition): Double {
        val lat1 = Math.toRadians(from.latitudeDegrees)
        val lat2 = Math.toRadians(to.latitudeDegrees)
        val deltaLon = Math.toRadians(to.longitudeDegrees - from.longitudeDegrees)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        if (y == 0.0 && x == 0.0) return 0.0
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Ângulo de elevação da aeronave acima do horizonte, em graus [0, 90].
     *
     * Aproximação de terra plana: `atan(altitude / distância horizontal)`. Diretamente por cima
     * (distância 0) devolve 90.
     *
     * TODO(geo): considerar a curvatura da Terra. **A condição que adiava isto deixou de valer** — a
     *  `004-settings` tornou o raio configurável até 150 km, que era exatamente o gatilho escrito aqui.
     *  Medido a 2026-09-11, para uma aeronave a 12 km, o erro de sobrestimação da elevação:
     *
     *  | Distância | Queda do horizonte (d²/2R) | Erro na elevação |
     *  |---|---|---|
     *  | 30 km *(raio de origem)* | 71 m | 0,12° |
     *  | 60 km | 283 m | 0,26° |
     *  | 100 km | 785 m | 0,44° |
     *  | 150 km *(raio máximo)* | 1 766 m | 0,67° |
     *
     *  Fica adiado ainda assim, e por uma razão melhor do que a anterior: **corrigir só a curvatura
     *  não é claramente mais correto.** A refração atmosférica curva a linha de vista no sentido
     *  oposto e cancela cerca de 15% do efeito — a prática usual é um raio da Terra efetivo (≈7/6 R)
     *  em vez do geométrico. Subtrair `d²/2R` a seco corrigia o sinal e passava ao lado da magnitude.
     *  Escolher o modelo é decisão para o `architect`, não remendo de véspera de release.
     *
     *  Consequência de ficar como está: uma aeronave a menos de 0,7° abaixo do ângulo mínimo pode
     *  entrar na lista aos raios maiores. No raio de origem são 0,12°, indistinguível para quem olha
     *  para o céu.
     */
    fun elevationDegrees(horizontalDistanceMeters: Double, altitudeMeters: Double): Double {
        if (altitudeMeters <= 0.0) return 0.0
        if (horizontalDistanceMeters <= 0.0) return 90.0
        return Math.toDegrees(atan2(altitudeMeters, horizontalDistanceMeters))
    }

    /**
     * Caixas envolventes que cobrem um círculo de [radiusMeters] à volta de [center].
     *
     * Devolve duas caixas quando o círculo cruza o antimeridiano (±180°) e uma caixa de longitude
     * completa quando abrange um polo — as APIs de voo só aceitam caixas com
     * `minLongitude <= maxLongitude`, por isso a divisão tem de acontecer aqui.
     */
    fun boundingBoxesAround(center: GeoPosition, radiusMeters: Double): List<BoundingBox> {
        require(radiusMeters > 0) { "Raio tem de ser positivo: $radiusMeters" }

        val deltaLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS)
        val minLat = center.latitudeDegrees - deltaLat
        val maxLat = center.latitudeDegrees + deltaLat

        val cosLat = cos(Math.toRadians(center.latitudeDegrees))
        val touchesPole = minLat <= -90.0 || maxLat >= 90.0 || abs(cosLat) < POLE_COS_EPSILON
        if (touchesPole) {
            return listOf(
                BoundingBox(
                    minLatitude = minLat.coerceAtLeast(-90.0),
                    minLongitude = -180.0,
                    maxLatitude = maxLat.coerceAtMost(90.0),
                    maxLongitude = 180.0,
                ),
            )
        }

        val deltaLon = Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * cosLat))
        if (deltaLon >= 180.0) {
            return listOf(BoundingBox(minLat, -180.0, maxLat, 180.0))
        }

        val rawMinLon = center.longitudeDegrees - deltaLon
        val rawMaxLon = center.longitudeDegrees + deltaLon
        return when {
            rawMinLon < -180.0 -> listOf(
                BoundingBox(minLat, -180.0, maxLat, rawMaxLon),
                BoundingBox(minLat, rawMinLon + 360.0, maxLat, 180.0),
            )
            rawMaxLon > 180.0 -> listOf(
                BoundingBox(minLat, rawMinLon, maxLat, 180.0),
                BoundingBox(minLat, -180.0, maxLat, rawMaxLon - 360.0),
            )
            else -> listOf(BoundingBox(minLat, rawMinLon, maxLat, rawMaxLon))
        }
    }

    companion object {
        /** Raio médio da Terra (IUGG), em metros. */
        const val EARTH_RADIUS_METERS = 6_371_008.8

        /** Abaixo deste cosseno da latitude a caixa degenera e passa a cobrir toda a longitude. */
        private const val POLE_COS_EPSILON = 1e-6
    }
}
