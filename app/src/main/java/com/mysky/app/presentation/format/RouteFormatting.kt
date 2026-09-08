package com.mysky.app.presentation.format

import com.mysky.app.domain.model.Route

/**
 * Apresentação da rota, partilhada pela lista e pelo detalhe.
 *
 * Uma só função pelo mesmo motivo do resto deste pacote: dois sítios a compor "LIS → CDG" à sua
 * maneira acabariam a divergir num separador ou numa ordem, e a diferença entre os dois ecrãs seria
 * indistinguível de um erro de dados para quem olha.
 *
 * O sentido do percurso não é opcional (FR-003): sem a seta, o utilizador teria de adivinhar qual
 * das siglas é a origem — e adivinharia metade das vezes ao contrário.
 */
object RouteFormatting {

    /** Devolve `null` quando não há rota, para quem apresenta omitir a linha por completo. */
    fun pairOrNull(route: Route?): Pair<String, String>? =
        route?.let { it.originIata to it.destinationIata }
}
