package com.mysky.app.domain.model

/**
 * O que o trabalho de fundo apurou da última vez, tal como é persistido.
 *
 * Guarda o **facto**, nunca a interpretação: quais as aeronaves e **quando** foram observadas, e não
 * a frase "está no teu céu". A razão é aritmética e não estética — a cadência por omissão são 30
 * minutos e a janela de frescura são 5. Se o texto fosse decidido no momento da escrita, o widget
 * afirmaria presença durante **25 dos 30 minutos** em que isso já era falso. Quem transforma isto em
 * texto é [SkyWidgetState.evaluate], no instante em que o widget é composto (AD-024).
 *
 * As três variantes são seladas em vez de um só tipo com campos a `null` porque não são a mesma coisa
 * com informação em falta: "o céu estava vazio" e "faltou a permissão" são resultados diferentes de
 * ciclos que ambos correram até ao fim.
 *
 * A ausência de snapshot — o `null` que o repositório devolve — é a quarta situação, "nunca correu", e
 * é distinta de [EmptySky]. Confundi-las seria dizer ao utilizador que o céu está vazio sem alguma vez
 * lá ter olhado.
 */
sealed interface SkyWidgetSnapshot {

    val observedAtEpochSeconds: Long

    /** O ciclo correu e encontrou aeronaves. [count] existe porque "1 avião" e "7 aviões" informam. */
    data class Flights(
        val top: WidgetFlight,
        val count: Int,
        override val observedAtEpochSeconds: Long,
    ) : SkyWidgetSnapshot

    /** O ciclo correu e não havia nada. Distinto de nunca ter corrido. */
    data class EmptySky(
        override val observedAtEpochSeconds: Long,
    ) : SkyWidgetSnapshot

    /** O ciclo terminou por falta de permissão de localização — sem ir à rede e sem gastar consulta. */
    data class PermissionMissing(
        override val observedAtEpochSeconds: Long,
    ) : SkyWidgetSnapshot
}

/**
 * Só o que o widget desenha.
 *
 * Deliberadamente **não** é um [OverheadFlight]. O widget mostra três campos; persistir o modelo
 * inteiro guardaria posição, rumo, rota, altitude e velocidade que ninguém lê, e amarraria o formato
 * gravado em disco à evolução de um modelo de domínio que muda por outras razões.
 *
 * Os dois `null` são reais e não descuido: a FR-005 exige que a falta de indicativo ou de companhia
 * nunca esconda a aeronave.
 */
data class WidgetFlight(
    val callsign: String?,
    val airlineName: String?,
    val elevationDegrees: Double,
)
