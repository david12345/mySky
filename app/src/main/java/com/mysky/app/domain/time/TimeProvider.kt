package com.mysky.app.domain.time

/**
 * Único ponto por onde o tempo real entra no sistema.
 *
 * Existe para que o princípio I se mantenha sem tocar em `DetectOverheadFlightsUseCase`, que
 * continua a receber `nowEpochSeconds` como parâmetro puro: é o `ObserveSkyUseCase` que obtém o
 * valor aqui e o passa adiante. Nos testes é substituído por um valor fixo.
 */
fun interface TimeProvider {
    fun nowEpochSeconds(): Long
}
