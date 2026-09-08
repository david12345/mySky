package com.mysky.app.domain.model

/**
 * Em que ponto está a atualização da tabela de rotas.
 *
 * Selado com estas quatro variantes porque são exatamente as situações que o ecrã de definições tem
 * de distinguir: nada a acontecer, a acontecer, correu bem — e com que resultado — ou falhou, e
 * porquê.
 */
sealed interface RouteUpdateState {

    data object Idle : RouteUpdateState

    data object InProgress : RouteUpdateState

    /**
     * Transporta a data e a contagem da tabela nova.
     *
     * Sem isso, o ecrã só poderia dizer "concluído", que não informa nada: o utilizador pediu a
     * atualização precisamente para saber se ficou com dados melhores.
     */
    data class Success(
        val generatedAtEpochSeconds: Long,
        val routeCount: Int,
    ) : RouteUpdateState

    data class Failure(val reason: RouteUpdateError) : RouteUpdateState
}

/**
 * A causa da falha, no vocabulário do utilizador e não no da rede.
 *
 * Existe pela mesma razão que o `SkyError` (AD-010): o ecrã tem de distinguir "não tens rede" de "o
 * servidor não respondeu" de "o que veio não presta", e não pode fazê-lo a inspecionar exceções.
 */
enum class RouteUpdateError {
    NoConnection,
    Unreachable,

    /** O ficheiro chegou mas não é uma tabela utilizável. A anterior fica. */
    InvalidData,
    Unexpected,
}
