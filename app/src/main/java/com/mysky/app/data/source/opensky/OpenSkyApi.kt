package com.mysky.app.data.source.opensky

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Endpoints REST da OpenSky Network usados pela app.
 *
 * Uso anónimo é gratuito mas tem limites baixos (créditos por dia e resolução temporal reduzida);
 * uma conta gratuita aumenta os limites. Sem chave configurada a app funciona na mesma, apenas
 * com refrescamentos menos frequentes.
 *
 * Documentação: https://openskynetwork.github.io/opensky-api/rest.html
 */
interface OpenSkyApi {

    /** Vetores de estado dentro de uma caixa envolvente (todos os valores em graus decimais). */
    @GET("states/all")
    suspend fun getStates(
        @Query("lamin") minLatitude: Double,
        @Query("lomin") minLongitude: Double,
        @Query("lamax") maxLatitude: Double,
        @Query("lomax") maxLongitude: Double,
    ): OpenSkyStatesResponse
}
