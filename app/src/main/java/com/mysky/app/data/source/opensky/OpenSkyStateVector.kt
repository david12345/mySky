package com.mysky.app.data.source.opensky

import kotlinx.serialization.json.JsonArray

/**
 * Leitura por índice de um vetor de estado da OpenSky.
 *
 * A ordem dos campos faz parte do contrato da API e está documentada em
 * https://openskynetwork.github.io/opensky-api/rest.html#response
 *
 * TODO(feature/sky-list): implementar a extração dos campos e cobrir com testes de contrato
 *  (incluindo arrays mais curtos do que o esperado e valores `null` em qualquer posição).
 */
object OpenSkyStateVector {
    const val INDEX_ICAO24 = 0
    const val INDEX_CALLSIGN = 1
    const val INDEX_ORIGIN_COUNTRY = 2
    const val INDEX_TIME_POSITION = 3
    const val INDEX_LAST_CONTACT = 4
    const val INDEX_LONGITUDE = 5
    const val INDEX_LATITUDE = 6
    const val INDEX_BARO_ALTITUDE = 7
    const val INDEX_ON_GROUND = 8
    const val INDEX_VELOCITY = 9
    const val INDEX_TRUE_TRACK = 10
    const val INDEX_VERTICAL_RATE = 11
    const val INDEX_GEO_ALTITUDE = 13

    fun parse(raw: JsonArray): OpenSkyStateVectorDto {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }
}

/** Vetor de estado já com os campos nomeados, ainda no formato da fonte. */
data class OpenSkyStateVectorDto(
    val icao24: String,
    val callsign: String?,
    val originCountry: String?,
    val longitude: Double?,
    val latitude: Double?,
    val barometricAltitudeMeters: Double?,
    val geometricAltitudeMeters: Double?,
    val onGround: Boolean,
    val velocityMetersPerSecond: Double?,
    val trueTrackDegrees: Double?,
    val verticalRateMetersPerSecond: Double?,
    val lastContactEpochSeconds: Long?,
)
