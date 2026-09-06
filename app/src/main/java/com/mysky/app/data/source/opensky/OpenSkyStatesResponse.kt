package com.mysky.app.data.source.opensky

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/**
 * Resposta de `GET /states/all`.
 *
 * A OpenSky devolve cada aeronave como um array heterogéneo (posicional), não como objeto — daí
 * [states] ser uma lista de [JsonArray] e a conversão viver em [OpenSkyStateVector].
 */
@Serializable
data class OpenSkyStatesResponse(
    val time: Long = 0L,
    val states: List<JsonArray>? = null,
)
