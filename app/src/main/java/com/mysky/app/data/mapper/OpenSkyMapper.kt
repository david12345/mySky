package com.mysky.app.data.mapper

import com.mysky.app.data.source.opensky.OpenSkyStateVectorDto
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.GeoPosition

/**
 * Converte DTOs da OpenSky em modelos de domínio. Fronteira única entre o formato da fonte e o
 * resto da app — qualquer nova fonte traz o seu próprio mapper.
 *
 * TODO(feature/sky-list): implementar, incluindo o trim do callsign (a API preenche com espaços)
 *  e a rejeição de posições inválidas em vez de deixar rebentar o `require` de [GeoPosition].
 */
fun OpenSkyStateVectorDto.toDomain(): Aircraft? {
    TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
}
