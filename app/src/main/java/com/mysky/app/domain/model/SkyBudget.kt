package com.mysky.app.domain.model

/**
 * O que a cadência do widget custa ao tempo de ecrã disponível por dia.
 *
 * O widget e o ecrã bebem do **mesmo** orçamento diário da fonte anónima, e o utilizador não tem como
 * descobrir isso sozinho: escolhe uma cadência mais frequente e, semanas depois, a app deixa de
 * atualizar mais cedo à tarde, sem relação aparente. A FR-027 existe para pôr o custo à vista no
 * momento da escolha.
 *
 * **O 400 vive aqui e só aqui** (AD-027). O defeito que a revisão da 004 encontrou foi um número — o
 * teto de altitude — escrito de duas maneiras que divergiram, com o aviso a acender para todos os
 * utilizadores. O risco simétrico nesta feature seria repetir o orçamento num ficheiro de UI.
 */
object SkyBudget {

    /** Consultas por dia de um utilizador anónimo da fonte de posições. Apurado na feature 004. */
    const val DAILY_QUERY_BUDGET = 400

    /** O laço de ecrã gasta uma consulta a cada 30 segundos (AD-008). */
    const val SCREEN_CYCLE_SECONDS = 30L

    private const val MINUTES_PER_DAY = 24 * 60

    /** Quantas consultas o trabalho de fundo gasta por dia com esta cadência. */
    fun queriesPerDay(intervalMinutes: Long): Int =
        if (intervalMinutes <= 0) 0 else (MINUTES_PER_DAY / intervalMinutes).toInt()

    /** Que fatia do orçamento diário isso representa, entre 0 e 1. */
    fun budgetShare(intervalMinutes: Long): Double =
        queriesPerDay(intervalMinutes).toDouble() / DAILY_QUERY_BUDGET

    /**
     * Quanto tempo de ecrã aberto sobra por dia, em segundos, depois de o widget se servir.
     *
     * Nunca negativo: uma cadência absurda esgotaria o orçamento sozinha, e o que o utilizador precisa
     * de ler nesse caso é "zero", não um número negativo.
     */
    fun remainingScreenSeconds(intervalMinutes: Long): Long {
        val remaining = (DAILY_QUERY_BUDGET - queriesPerDay(intervalMinutes)).coerceAtLeast(0)
        return remaining * SCREEN_CYCLE_SECONDS
    }
}
