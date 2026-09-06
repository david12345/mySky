package com.mysky.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.notification.OverheadNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Trabalho periódico que alimenta o widget e as notificações.
 *
 * Restrições de plataforma que este worker tem de respeitar:
 *  - o intervalo mínimo de `PeriodicWorkRequest` é 15 minutos e o sistema pode adiar mais em
 *    Doze/App Standby: a app nunca deve prometer tempo real ao utilizador;
 *  - uma execução falhada por falta de rede devolve `Result.retry()`, não `failure()`;
 *  - sem permissão de localização o trabalho termina com `Result.success()` e um estado de widget
 *    explicativo — repetir não resolveria nada e só gastaria bateria.
 *
 * TODO(feature/widget): obter localização, chamar [ObserveSkyUseCase], guardar o resultado no
 *  estado do Glance, pedir a atualização do widget e delegar em [OverheadNotifier] quando as
 *  notificações estiverem ativas.
 */
@HiltWorker
class SkyRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val observeSky: ObserveSkyUseCase,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val overheadNotifier: OverheadNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        TODO("Implementar durante a feature 'widget' (ver .specify/)")
    }

    companion object {
        const val PERIODIC_WORK_NAME = "mysky_sky_refresh_periodic"
        const val ONE_TIME_WORK_NAME = "mysky_sky_refresh_once"
    }
}
