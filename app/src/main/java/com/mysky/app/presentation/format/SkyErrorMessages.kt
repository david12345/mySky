package com.mysky.app.presentation.format

import androidx.annotation.StringRes
import com.mysky.app.R
import com.mysky.app.domain.model.SkyError

/**
 * Tradução de [SkyError] para o que o utilizador lê.
 *
 * Uma mensagem distinta por causa (FR-024): "sem Internet", "o serviço não respondeu" e "não
 * consegui saber onde estás" pedem coisas diferentes a quem lê. Um genérico "ocorreu um erro"
 * deixaria o utilizador sem saber se o problema é dele, nosso, ou da rede.
 */
@StringRes
fun SkyError.messageRes(): Int = when (this) {
    SkyError.NoConnection -> R.string.sky_error_no_connection
    is SkyError.FlightServiceUnavailable -> R.string.sky_error_service_unavailable
    is SkyError.RateLimited -> R.string.sky_error_rate_limited
    SkyError.LocationUnavailable -> R.string.sky_error_location_unavailable
    is SkyError.Unexpected -> R.string.sky_error_unexpected
}

/**
 * Todos os erros oferecem repetição, incluindo o de excesso de pedidos: mesmo quando a app já
 * está a espaçar sozinha, tirar o botão deixaria o utilizador sem nada para fazer senão sair.
 */
@get:StringRes
val SkyError.retryActionRes: Int
    get() = R.string.sky_error_retry
