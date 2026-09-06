package com.mysky.app.domain.repository

import com.mysky.app.domain.model.Airline

/**
 * Tabela de operadores aéreos distribuída com a app.
 *
 * Porta própria e não uma responsabilidade do `FlightDataSource` (AD-007): o princípio II fala de
 * *fontes de dados de voo*, e isto é dado de referência estático, não um fornecedor de posições.
 */
interface AirlineDirectory {
    /**
     * Operador correspondente ao prefixo de [callsign], ou `null` se o indicativo não tiver
     * prefixo válido ou o prefixo não constar da tabela.
     *
     * **Nunca lança.** Um asset ausente ou ilegível degrada para tabela vazia: uma falha a
     * resolver o nome da companhia não pode esconder a aeronave (FR-012).
     */
    suspend fun findByCallsign(callsign: String?): Airline?
}
