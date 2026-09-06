package com.mysky.app.data.source.opensky

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Leitura por índice de um vetor de estado da OpenSky.
 *
 * A ordem dos campos faz parte do contrato da API e está documentada em
 * https://openskynetwork.github.io/opensky-api/rest.html#response
 *
 * **A leitura é deliberadamente tolerante.** A API já cresceu antes: um array mais curto do que o
 * esperado, `null` em qualquer posição opcional e campos novos no fim são todos normais e não
 * podem invalidar o registo. A validação — e a decisão de descartar — pertence ao mapeamento para
 * o domínio, não a esta leitura posicional.
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

    fun parse(raw: JsonArray): OpenSkyStateVectorDto = OpenSkyStateVectorDto(
        // Os índices 0 e 8 são os únicos que a fonte promete não serem nulos. Se ainda assim
        // vierem vazios, o valor de recurso faz o mapeamento descartar a aeronave em silêncio,
        // em vez de rebentar e levar consigo o resto da resposta.
        icao24 = raw.stringAt(INDEX_ICAO24).orEmpty(),
        callsign = raw.stringAt(INDEX_CALLSIGN),
        originCountry = raw.stringAt(INDEX_ORIGIN_COUNTRY),
        // Longitude é o índice 5 e latitude o 6 — ordem invertida face à convenção habitual.
        longitude = raw.doubleAt(INDEX_LONGITUDE),
        latitude = raw.doubleAt(INDEX_LATITUDE),
        barometricAltitudeMeters = raw.doubleAt(INDEX_BARO_ALTITUDE),
        geometricAltitudeMeters = raw.doubleAt(INDEX_GEO_ALTITUDE),
        onGround = raw.booleanAt(INDEX_ON_GROUND) ?: false,
        velocityMetersPerSecond = raw.doubleAt(INDEX_VELOCITY),
        trueTrackDegrees = raw.doubleAt(INDEX_TRUE_TRACK),
        verticalRateMetersPerSecond = raw.doubleAt(INDEX_VERTICAL_RATE),
        lastContactEpochSeconds = raw.longAt(INDEX_LAST_CONTACT),
    )

    /** `null` tanto para índice inexistente como para `JsonNull` — para quem lê, é o mesmo. */
    private fun JsonArray.stringAt(index: Int): String? =
        (getOrNull(index) as? JsonPrimitive)?.contentOrNull

    private fun JsonArray.doubleAt(index: Int): Double? = stringAt(index)?.toDoubleOrNull()

    private fun JsonArray.longAt(index: Int): Long? = stringAt(index)?.toLongOrNull()

    private fun JsonArray.booleanAt(index: Int): Boolean? = stringAt(index)?.toBooleanStrictOrNull()
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
