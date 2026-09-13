package com.mysky.app.worker

/**
 * Manda repintar todos os widgets no ecrã.
 *
 * Existe como interface, e sem um único tipo do Glance na assinatura, para o `worker/` **não**
 * importar `androidx.glance` (AD-025). Importá-lo acoplaria um componente de fundo — testável na JVM
 * com duplos — a um toolkit de UI que exigiria Robolectric ou instrumentação, que este projeto não
 * tem e cuja ausência é deliberada.
 *
 * A implementação vive em `widget/`, e é o `widget/` que depende do `worker/` — nunca o contrário.
 */
fun interface WidgetRefresher {
    suspend fun refreshAll()
}
