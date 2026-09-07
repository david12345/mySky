package com.mysky.app.domain.model

/**
 * A aeronave que o utilizador está a ver ainda está no céu dele?
 *
 * Três variantes porque são exatamente três as situações que o ecrã de detalhe tem de distinguir, e
 * confundi-las não produz erro nenhum — produz um resultado errado com ar de certo.
 *
 * Repare-se no que **não** existe aqui: uma variante para "a atualização falhou". Uma falha é
 * ortogonal à presença e vive no erro da observação. Um ciclo falhado nunca faz um avião sair do
 * céu; se fizesse, perder a rede passaria a anunciar que o avião partiu.
 */
sealed interface FlightPresence {

    /**
     * Nunca vista nesta sessão. Inclui o caso de o processo ter sido morto e restaurado com o
     * detalhe no topo da pilha: sem observação nova, não há nada de honesto a mostrar.
     */
    data object NeverObserved : FlightPresence

    /**
     * No céu do utilizador, com os valores da observação mais recente.
     *
     * [observedAtEpochSeconds] existe para a transição para [LeftSky] poder datar a última vez que
     * a aeronave **foi vista**, e não o instante em que se descobriu que já não estava — entre os
     * dois vai um ciclo inteiro, e usar o segundo faria o ecrã dar os valores por mais recentes do
     * que são.
     */
    data class Current(
        val flight: OverheadFlight,
        val observedAtEpochSeconds: Long,
    ) : FlightPresence

    /**
     * Já não cumpre os critérios do céu do utilizador.
     *
     * Transporta sempre o último voo conhecido **e** o instante em que foi visto: um sem o outro
     * permitiria apresentar dados antigos sem os datar, que é o que FR-020 proíbe.
     */
    data class LeftSky(
        val lastFlight: OverheadFlight,
        val lastSeenEpochSeconds: Long,
    ) : FlightPresence
}
