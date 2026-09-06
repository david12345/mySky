package com.mysky.app.domain.model

/**
 * Erros que o utilizador tem de conseguir distinguir (FR-024).
 *
 * Estende [Exception] para caber no `Result<T>` que os contratos do domínio já usam. É um
 * compromisso deliberado (AD-010): um erro de domínio passa a ser tecnicamente lançável. Mitiga-se
 * anulando o preenchimento do stack trace — que aqui nunca serve para nada e custa caro — e pela
 * regra de nunca o lançar: só embrulhado em `Result.failure`.
 *
 * A tradução de exceções de rede para estas variantes acontece na fronteira do repositório.
 */
sealed class SkyError(message: String) : Exception(message) {

    /** Sem rota para a rede: modo de avião, sem cobertura, Wi-Fi sem saída. */
    data object NoConnection : SkyError("Sem ligação à Internet")

    /** A fonte de voos respondeu com erro ou não respondeu de todo. */
    data class FlightServiceUnavailable(val httpCode: Int? = null) :
        SkyError("O serviço de voos não respondeu (HTTP $httpCode)")

    /**
     * Excesso de pedidos à fonte. [retryAfterSeconds] vem do cabeçalho da resposta quando existe.
     *
     * Não é reintentado em silêncio: sobe até ao ViewModel, que alonga a espera seguinte e informa
     * o utilizador. Reintentar gastaria o orçamento diário exatamente quando ele já se esgotou.
     */
    data class RateLimited(val retryAfterSeconds: Long? = null) :
        SkyError("Demasiados pedidos ao serviço de voos")

    /** Permissão concedida mas sem posição possível. Distinto de falha de rede (FR-024). */
    data object LocationUnavailable : SkyError("Não foi possível obter a localização")

    data class Unexpected(override val cause: Throwable? = null) :
        SkyError("Erro inesperado: ${cause?.message}")

    /** O stack trace de um erro de domínio não diz nada a ninguém e custa uma cópia da pilha. */
    override fun fillInStackTrace(): Throwable = this
}
