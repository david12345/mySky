package com.mysky.app.domain.usecase

import com.mysky.app.domain.geo.GeoCalculator
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadCriteria
import com.mysky.app.domain.model.OverheadFlight
import javax.inject.Inject

/**
 * Núcleo da app: decide quais das aeronaves recebidas estão de facto no céu do observador.
 *
 * Regras (todas configuráveis via [OverheadCriteria]):
 *  1. descarta aeronaves sem posição, no solo, ou com vetor de estado demasiado antigo;
 *  2. descarta abaixo da altitude mínima (tráfego rasante, helicópteros locais, ruído);
 *  3. exige distância horizontal <= raio configurado;
 *  4. exige elevação >= limiar configurado — é isto que distingue "está perto" de "está por cima".
 *
 * Sem dependências de rede, Android ou UI: totalmente testável na JVM.
 */
class DetectOverheadFlightsUseCase @Inject constructor(
    private val geoCalculator: GeoCalculator,
) {

    /**
     * @param nowEpochSeconds instante de referência para avaliar a idade dos vetores de estado;
     *   injetado como parâmetro (em vez de ler o relógio) para manter o caso de uso determinístico.
     * @return aeronaves no céu, da mais relevante (mais alta no céu) para a menos relevante.
     */
    operator fun invoke(
        observer: GeoPosition,
        aircraft: List<Aircraft>,
        criteria: OverheadCriteria = OverheadCriteria(),
        nowEpochSeconds: Long,
    ): List<OverheadFlight> = aircraft
        .asSequence()
        .filter { it.isUsable(criteria, nowEpochSeconds) }
        .mapNotNull { it.toOverheadCandidate(observer) }
        .filter { it.horizontalDistanceMeters <= criteria.maxHorizontalDistanceMeters }
        .filter { it.elevationDegrees >= criteria.minElevationDegrees }
        .sortedByDescending { it.relevanceScore }
        .toList()

    private fun Aircraft.isUsable(criteria: OverheadCriteria, nowEpochSeconds: Long): Boolean {
        if (onGround || position == null) return false
        val altitude = altitudeMeters ?: return false
        if (altitude < criteria.minAltitudeMeters) return false
        val lastContact = lastContactEpochSeconds ?: return true
        return nowEpochSeconds - lastContact <= criteria.maxStateAgeSeconds
    }

    private fun Aircraft.toOverheadCandidate(observer: GeoPosition): OverheadFlight? {
        val position = position ?: return null
        val altitude = altitudeMeters ?: return null
        val distance = geoCalculator.distanceMeters(observer, position)
        return OverheadFlight(
            aircraft = this,
            horizontalDistanceMeters = distance,
            bearingDegrees = geoCalculator.bearingDegrees(observer, position),
            elevationDegrees = geoCalculator.elevationDegrees(distance, altitude),
        )
    }
}
