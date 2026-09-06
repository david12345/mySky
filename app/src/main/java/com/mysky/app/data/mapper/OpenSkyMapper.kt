package com.mysky.app.data.mapper

import com.mysky.app.data.source.opensky.OpenSkyStateVectorDto
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.GeoPosition

/**
 * Converte DTOs da OpenSky em modelos de domínio. Fronteira única entre o formato da fonte e o
 * resto da app — qualquer nova fonte traz o seu próprio mapper.
 *
 * Devolve `null` para o registo que não é utilizável. Descartar aqui, em silêncio, é o contrato:
 * um vetor de estado corrompido não pode invalidar os outros duzentos que vieram na mesma resposta.
 */
fun OpenSkyStateVectorDto.toDomain(): Aircraft? {
    if (icao24.isBlank()) return null

    val position = when {
        // Sem coordenadas a aeronave sobrevive ao mapeamento: é a deteção que a filtra, e assim
        // uma fonte que reporta velocidade sem posição não perde a aeronave por completo.
        latitude == null || longitude == null -> null
        // Fora de intervalo é dado corrompido, não dado em falta: o registo inteiro vai fora.
        // O `require` de GeoPosition rebentaria aqui, e uma exceção levaria a resposta toda.
        latitude !in MIN_LATITUDE..MAX_LATITUDE -> return null
        longitude !in MIN_LONGITUDE..MAX_LONGITUDE -> return null
        else -> GeoPosition(latitudeDegrees = latitude, longitudeDegrees = longitude)
    }

    return Aircraft(
        icao24 = icao24.trim().lowercase(),
        // A fonte preenche o indicativo com espaços à direita ("TAP1234 "); sem trim, qualquer
        // comparação ou extração de prefixo falha de forma silenciosa.
        callsign = callsign?.trim()?.takeIf { it.isNotEmpty() },
        originCountry = originCountry?.trim()?.takeIf { it.isNotEmpty() },
        position = position,
        barometricAltitudeMeters = barometricAltitudeMeters,
        geometricAltitudeMeters = geometricAltitudeMeters,
        groundSpeedMetersPerSecond = velocityMetersPerSecond,
        headingDegrees = trueTrackDegrees,
        verticalRateMetersPerSecond = verticalRateMetersPerSecond,
        onGround = onGround,
        lastContactEpochSeconds = lastContactEpochSeconds,
    )
}

private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
