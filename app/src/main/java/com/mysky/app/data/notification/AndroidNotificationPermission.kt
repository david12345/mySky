package com.mysky.app.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.mysky.app.domain.repository.NotificationPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `areNotificationsEnabled()` e não `checkSelfPermission(POST_NOTIFICATIONS)`.
 *
 * A diferença importa: aquela cobre, de uma só pergunta, a permissão de runtime da API 33+ **e** o
 * interruptor de "notificações desativadas" que existe em todas as versões do Android desde muito
 * antes disso. Verificar só a permissão daria `true` num telefone onde o utilizador desligou as
 * notificações da app à mão — e a app passaria a publicar avisos que ninguém veria, convencida de que
 * estava a funcionar.
 */
@Singleton
class AndroidNotificationPermission @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPermission {

    override fun isGranted(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
