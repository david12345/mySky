package com.mysky.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mysky.app.domain.repository.SkyWidgetRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.RunSkyCycleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Trabalho periódico que alimenta o widget e, a partir da feature seguinte, as notificações.
 *
 * **Não tem lógica.** A sequência do ciclo vive no [RunSkyCycleUseCase], partilhada com o ecrã
 * (AD-028), e a decisão do que gravar e do que devolver vive no [SkyRefreshDecision], que é uma
 * função pura. O que sobra aqui é encadeamento — e é assim de propósito: sem `work-testing` nem
 * Robolectric, tudo o que ficasse dentro desta classe deixava de ser verificável.
 *
 * Restrições de plataforma respeitadas a montante: o mínimo de 15 minutos vem de
 * `SkySettings.coerced()`, as `Constraints` de rede e o backoff do [SkyWorkScheduler].
 */
@HiltWorker
class SkyRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val runSkyCycle: RunSkyCycleUseCase,
    private val skyWidgetRepository: SkyWidgetRepository,
    private val widgetRefresher: WidgetRefresher,
    private val timeProvider: TimeProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val decision = SkyRefreshDecision.decide(
            result = runSkyCycle(),
            nowEpochSeconds = timeProvider.nowEpochSeconds(),
        )

        // `null` significa "não toques no que lá está" — é o que faz uma falha nunca apagar o que o
        // utilizador estava a ver (FR-014).
        decision.snapshot?.let { skyWidgetRepository.save(it) }

        // Repinta **sempre**, mesmo quando falhou e mesmo quando não há nada novo. É o que limpa o
        // "a atualizar" e mostra a razão da falha. Repintar só quando havia snapshot novo deixaria o
        // widget preso em "A atualizar…" para sempre a seguir a uma falha — que foi exatamente o
        // defeito encontrado na feature 003, no ecrã da tabela de rotas.
        widgetRefresher.refreshAll(decision.feedback)

        return when (decision.outcome) {
            WorkOutcome.Success -> Result.success()
            WorkOutcome.Retry -> Result.retry()
            WorkOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "mysky_sky_refresh_periodic"
        const val ONE_TIME_WORK_NAME = "mysky_sky_refresh_once"
    }
}
