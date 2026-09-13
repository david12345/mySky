package com.mysky.app.worker

/**
 * Há pelo menos um widget no ecrã inicial?
 *
 * É a pergunta que substitui a contagem de eventos `onEnabled`/`onDisabled` (AD-026). Perguntar pelo
 * estado real em vez de acumular deltas é o que resolve o caso que nenhum evento cobre: quem já tinha
 * o widget antes de a app ser atualizada nunca mais recebe `onEnabled`, e ficaria sem trabalho de
 * fundo para sempre.
 */
fun interface WidgetPresenceCheck {
    suspend fun hasAnyWidget(): Boolean
}
