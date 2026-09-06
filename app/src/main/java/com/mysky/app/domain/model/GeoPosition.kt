package com.mysky.app.domain.model

/**
 * Ponto geográfico em graus decimais (WGS84).
 *
 * Modelo de domínio puro: não depende de `android.location.Location` nem de qualquer DTO de rede.
 */
data class GeoPosition(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
) {
    init {
        require(latitudeDegrees in -90.0..90.0) { "Latitude fora de intervalo: $latitudeDegrees" }
        require(longitudeDegrees in -180.0..180.0) { "Longitude fora de intervalo: $longitudeDegrees" }
    }
}
