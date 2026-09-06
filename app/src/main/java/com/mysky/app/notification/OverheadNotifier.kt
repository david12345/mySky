package com.mysky.app.notification

import android.content.Context
import com.mysky.app.domain.model.OverheadFlight
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificações de "há um avião por cima de ti".
 *
 * Regras de produto e de plataforma:
 *  - desativadas por omissão; só disparam com o utilizador a ter ativado explicitamente;
 *  - exigem `POST_NOTIFICATIONS` (Android 13+) pedida em runtime, com explicação prévia;
 *  - deduplicadas por `icao24` dentro de uma janela temporal, via
 *    [com.mysky.app.domain.repository.SightingRepository.wasNotifiedRecently] — a mesma passagem
 *    não pode gerar várias notificações;
 *  - nunca justificam um foreground service permanente (ver constituição do projeto).
 *
 * TODO(feature/notifications): criar o canal de notificação, construir a notificação com
 *  callsign/companhia/altitude e abrir o ecrã de detalhe no toque.
 */
@Singleton
class OverheadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        TODO("Implementar durante a feature 'notificações' (ver .specify/)")
    }

    fun notifyOverhead(flight: OverheadFlight) {
        TODO("Implementar durante a feature 'notificações' (ver .specify/)")
    }

    companion object {
        const val CHANNEL_ID = "overhead_flights"
    }
}
