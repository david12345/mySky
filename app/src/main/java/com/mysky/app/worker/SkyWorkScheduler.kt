package com.mysky.app.worker

import android.content.Context
import androidx.work.WorkManager
import com.mysky.app.domain.model.SkySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponto único de agendamento do WorkManager. Nenhuma outra classe deve criar `WorkRequest`s:
 * assim o limite de 15 minutos e as `Constraints` de rede ficam garantidos num sítio só.
 *
 * TODO(feature/widget): implementar
 *  - `schedulePeriodicRefresh`: `PeriodicWorkRequestBuilder` com o intervalo das definições,
 *    forçado a >= [SkySettings.MIN_REFRESH_INTERVAL_MINUTES], `NetworkType.CONNECTED`,
 *    backoff exponencial e `ExistingPeriodicWorkPolicy.UPDATE`;
 *  - `cancelPeriodicRefresh`: quando não há widget nem notificações ativas;
 *  - `requestImmediateRefresh`: `OneTimeWorkRequest` expedito para o tap-to-refresh do widget.
 */
@Singleton
class SkyWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun schedulePeriodicRefresh(settings: SkySettings) {
        TODO("Implementar durante a feature 'widget' (ver .specify/)")
    }

    fun cancelPeriodicRefresh() {
        TODO("Implementar durante a feature 'widget' (ver .specify/)")
    }

    fun requestImmediateRefresh() {
        TODO("Implementar durante a feature 'widget' (ver .specify/)")
    }
}
