package com.mysky.app.domain.repository

import com.mysky.app.domain.model.RouteUpdateState
import kotlinx.coroutines.flow.Flow

/** Data e volume da tabela em uso, lidos do cabeçalho do próprio ficheiro. */
data class RouteTableInfo(
    val generatedAtEpochSeconds: Long,
    val routeCount: Int,
)

/**
 * A tabela de rotas vista do lado de quem a gere — distinta da [RouteDirectory], que é quem a
 * consulta.
 *
 * O ecrã de definições fala apenas com isto e **nunca** com o WorkManager: a tradução do estado do
 * trabalho para [RouteUpdateState] acontece na implementação, tal como a tradução de erros de rede
 * para `SkyError` acontece no repositório de voos (AD-010, AD-016).
 */
interface RouteTableRepository {

    val updateState: Flow<RouteUpdateState>

    /** `null` se não houver tabela legível — o que não devia acontecer, mas não pode rebentar. */
    suspend fun tableInfo(): RouteTableInfo?

    /**
     * Pede a atualização. Nunca acontece por iniciativa da app (FR-019).
     *
     * Um segundo pedido com um já em curso não duplica trabalho.
     */
    fun requestUpdate()
}
