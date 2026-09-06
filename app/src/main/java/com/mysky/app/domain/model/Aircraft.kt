package com.mysky.app.domain.model

/**
 * Aeronave tal como o domínio a conhece, independente da fonte de dados.
 *
 * Quase todos os campos são opcionais porque as fontes públicas (OpenSky, ADS-B Exchange, ...)
 * reportam vetores de estado incompletos com frequência.
 */
data class Aircraft(
    /** Identificador ICAO 24-bit em hexadecimal minúsculo. Único e estável por aeronave. */
    val icao24: String,
    /** Indicativo de chamada, já sem espaços de padding (ex.: "TAP1234"). */
    val callsign: String? = null,
    val originCountry: String? = null,
    val position: GeoPosition? = null,
    /** Altitude barométrica em metros acima do nível médio do mar. */
    val barometricAltitudeMeters: Double? = null,
    /** Altitude geométrica (GNSS) em metros. Preferida para o ângulo de elevação quando existe. */
    val geometricAltitudeMeters: Double? = null,
    val groundSpeedMetersPerSecond: Double? = null,
    /** Rumo da aeronave em graus, 0 = norte. */
    val headingDegrees: Double? = null,
    val verticalRateMetersPerSecond: Double? = null,
    val onGround: Boolean = false,
    /** Instante do último contacto reportado pela fonte (epoch, segundos UTC). */
    val lastContactEpochSeconds: Long? = null,
) {
    /** Altitude a usar nos cálculos geométricos: geométrica quando disponível, senão barométrica. */
    val altitudeMeters: Double?
        get() = geometricAltitudeMeters ?: barometricAltitudeMeters
}
