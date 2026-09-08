package com.mysky.app.presentation.format

import com.mysky.app.domain.model.Route

/**
 * Apresentação da rota, partilhada pela lista e pelo detalhe.
 *
 * Esta função devolve o **par**; quem compõe o texto com a seta é o recurso `route_pair`, um só,
 * partilhado pelos dois ecrãs. É essa partilha que impede a divergência — dois sítios a montar
 * "LIS → CDG" à sua maneira acabariam a discordar num separador ou numa ordem, e a diferença seria
 * indistinguível de um erro de dados para quem olha.
 *
 * O que a função garante é o outro lado: a ordem do par. O sentido do percurso não é opcional
 * (FR-003) — sem ele o utilizador teria de adivinhar qual das siglas é a origem, e adivinharia
 * metade das vezes ao contrário.
 */
object RouteFormatting {

    /** Devolve `null` quando não há rota, para quem apresenta omitir a linha por completo. */
    fun pairOrNull(route: Route?): Pair<String, String>? =
        route?.let { it.originIata to it.destinationIata }
}
