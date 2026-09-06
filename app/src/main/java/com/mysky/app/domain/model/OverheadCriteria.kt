package com.mysky.app.domain.model

/**
 * Limiares que definem "este avião está no meu céu".
 *
 * Derivado das preferências do utilizador ([SkySettings]) mas mantido separado para que
 * [com.mysky.app.domain.usecase.DetectOverheadFlightsUseCase] seja testável sem DataStore.
 */
data class OverheadCriteria(
    /** Raio horizontal máximo, em metros. */
    val maxHorizontalDistanceMeters: Double = DEFAULT_RADIUS_METERS,
    /** Elevação mínima acima do horizonte, em graus. */
    val minElevationDegrees: Double = DEFAULT_MIN_ELEVATION_DEGREES,
    /** Altitude mínima da aeronave, em metros. Filtra tráfego no solo e rasante. */
    val minAltitudeMeters: Double = DEFAULT_MIN_ALTITUDE_METERS,
    /** Idade máxima do vetor de estado, em segundos. Acima disto a posição é ruído. */
    val maxStateAgeSeconds: Long = DEFAULT_MAX_STATE_AGE_SECONDS,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 30_000.0
        const val DEFAULT_MIN_ELEVATION_DEGREES = 25.0
        const val DEFAULT_MIN_ALTITUDE_METERS = 300.0
        const val DEFAULT_MAX_STATE_AGE_SECONDS = 120L
    }
}
