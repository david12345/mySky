package com.mysky.app.data.route

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Há rede agora?
 *
 * Existe para a atualização da tabela poder dizer "não tens rede" **de imediato**, em vez de ficar
 * enfileirada à espera dela. O `WorkRequest` tem `NetworkType.CONNECTED`, o que significa que sem
 * rede o WorkManager simplesmente não corre o trabalho — e o utilizador ficaria a olhar para "A
 * atualizar…" durante horas, sem nada acontecer e sem nada explicar.
 *
 * A restrição no `WorkRequest` mantém-se, mas como rede de segurança para uma ligação que caia a
 * meio, e não como a forma de detetar ausência de rede à partida.
 */
interface NetworkAvailability {
    fun isOnline(): Boolean
}

@Singleton
class AndroidNetworkAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkAvailability {

    override fun isOnline(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
