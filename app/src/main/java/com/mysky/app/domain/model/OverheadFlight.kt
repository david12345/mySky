package com.mysky.app.domain.model

/**
 * Aeronave considerada visível no céu do observador, com a geometria já calculada.
 *
 * Produzido por [com.mysky.app.domain.usecase.DetectOverheadFlightsUseCase].
 */
data class OverheadFlight(
    val aircraft: Aircraft,
    /** Distância horizontal observador -> projeção da aeronave no solo, em metros. */
    val horizontalDistanceMeters: Double,
    /** Azimute do observador para a aeronave, em graus (0 = norte, 90 = este). */
    val bearingDegrees: Double,
    /** Elevação acima do horizonte, em graus (0 = horizonte, 90 = zénite). */
    val elevationDegrees: Double,
) {
    /**
     * Critério de ordenação: quanto mais alto no céu, mais "relevante" para o utilizador
     * (é o que o widget mostra). Usar sempre isto em vez de ordenar só por distância.
     */
    val relevanceScore: Double
        get() = elevationDegrees
}
