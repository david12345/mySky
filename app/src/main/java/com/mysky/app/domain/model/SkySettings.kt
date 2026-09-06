package com.mysky.app.domain.model

/** Unidade de distância escolhida pelo utilizador. */
enum class DistanceUnit { KILOMETERS, MILES }

/** Unidade de altitude escolhida pelo utilizador. */
enum class AltitudeUnit { METERS, FEET }

/**
 * Preferências persistidas do utilizador (ecrã de definições).
 *
 * [refreshIntervalMinutes] nunca deve ser inferior a [MIN_REFRESH_INTERVAL_MINUTES]: é o mínimo
 * imposto pelo WorkManager para trabalho periódico.
 */
data class SkySettings(
    val detectionRadiusMeters: Double = OverheadCriteria.DEFAULT_RADIUS_METERS,
    val minElevationDegrees: Double = OverheadCriteria.DEFAULT_MIN_ELEVATION_DEGREES,
    val minAltitudeMeters: Double = OverheadCriteria.DEFAULT_MIN_ALTITUDE_METERS,
    val refreshIntervalMinutes: Long = MIN_REFRESH_INTERVAL_MINUTES,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
    val notificationsEnabled: Boolean = false,
    val widgetEnabled: Boolean = true,
) {
    fun toCriteria(): OverheadCriteria = OverheadCriteria(
        maxHorizontalDistanceMeters = detectionRadiusMeters,
        minElevationDegrees = minElevationDegrees,
        minAltitudeMeters = minAltitudeMeters,
    )

    companion object {
        /** Mínimo imposto pelo Android para `PeriodicWorkRequest`. */
        const val MIN_REFRESH_INTERVAL_MINUTES = 15L
    }
}
