package com.mysky.app.domain.repository

import com.mysky.app.domain.model.Route

/**
 * Tabela de rotas, consultada pelo indicativo de voo.
 *
 * Porta própria e distinta do `FlightDataSource`, pela mesma razão da `AirlineDirectory`: dados de
 * referência não são um fornecedor de posições. Aqui não há rede — a tabela é local, e a sua
 * atualização é outro caminho, pelo `RouteTableRepository`.
 */
fun interface RouteDirectory {

    /**
     * @return a rota do indicativo, ou `null` se for desconhecido, inválido ou ausente.
     *
     * **Nunca lança.** Um ficheiro em falta, truncado ou ilegível degrada para tabela vazia e a
     * lista continua a funcionar sem rotas: a rota é decoração e nunca esconde uma aeronave.
     */
    suspend fun findByCallsign(callsign: String?): Route?
}
