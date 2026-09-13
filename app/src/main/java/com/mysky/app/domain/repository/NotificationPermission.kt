package com.mysky.app.domain.repository

/**
 * A app pode publicar notificações **neste momento**?
 *
 * Porto próprio, e não um campo em `SkySettings`, porque são dois dados de naturezas diferentes:
 * `notificationsEnabled` é a **intenção** do utilizador, guardada; isto é um **facto do sistema**,
 * lido. Misturá-los teria uma consequência concreta e má — quando o utilizador revogasse a permissão
 * nas definições do Android, a app reescreveria a intenção para falso e perdê-la-ia; ao voltar a
 * conceder, ele teria de tocar outra vez no interruptor sem razão nenhuma.
 *
 * Lido a cada uso, nunca guardado em cache: a permissão pode desaparecer entre dois ciclos sem a app
 * ser informada.
 */
fun interface NotificationPermission {
    fun isGranted(): Boolean
}
