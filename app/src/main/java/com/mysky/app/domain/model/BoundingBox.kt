package com.mysky.app.domain.model

/**
 * Caixa envolvente usada para pedir aeronaves a uma [com.mysky.app.data.source.FlightDataSource].
 *
 * Uma caixa nunca cruza o antimeridiano: quando o raio à volta do observador o atravessa,
 * [com.mysky.app.domain.geo.GeoCalculator.boundingBoxesAround] devolve duas caixas.
 */
data class BoundingBox(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    init {
        require(minLatitude <= maxLatitude) { "minLatitude > maxLatitude" }
        require(minLongitude <= maxLongitude) { "minLongitude > maxLongitude (usar duas caixas)" }
    }
}
