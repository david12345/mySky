package com.mysky.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import com.mysky.app.domain.model.SkySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O único ponto de criação de `WorkRequest`s do *sky refresh* (princípio IV, FR-023).
 *
 * Não decide **se** o trabalho deve existir — isso é do [SkyBackgroundWorkCoordinator] (AD-026).
 * Aqui só se sabe construir e enfileirar, o que mantém o mínimo de 15 minutos, as restrições de rede
 * e o backoff garantidos num sítio só.
 */
@Singleton
class SkyWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val networkRequired = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Agenda ou reagenda a cadência de fundo.
     *
     * O intervalo vem já corrigido de [SkySettings.coerced] (AD-022), por isso **não se valida aqui**:
     * duplicar a correção seria criar o segundo sítio onde o mesmo limite vive, que foi exatamente o
     * defeito que a revisão da 004 encontrou.
     *
     * `UPDATE` sobre um nome único é o que faz a FR-025 — mudar a cadência substitui o trabalho em vez
     * de acumular um segundo — e o que garante a FR-021: um só trabalho, haja quantos widgets houver.
     */
    fun schedulePeriodicRefresh(settings: SkySettings) {
        val request = PeriodicWorkRequestBuilder<SkyRefreshWorker>(
            settings.refreshIntervalMinutes, TimeUnit.MINUTES,
        )
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SkyRefreshWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodicRefresh() {
        workManager.cancelUniqueWork(SkyRefreshWorker.PERIODIC_WORK_NAME)
    }

    /**
     * O toque em "atualizar" do widget.
     *
     * `KEEP` e não `REPLACE`: dois toques enquanto um pedido está em curso valem por um (FR-017).
     * Com `REPLACE`, o segundo toque cancelaria a consulta já paga e começaria outra — gastando duas
     * do orçamento diário para obter um resultado.
     *
     * **Sem restrição de rede, ao contrário do trabalho periódico.** Com `NetworkType.CONNECTED`, um
     * toque sem rede deixava o pedido em `ENQUEUED` para sempre: o worker nunca corria, ninguém
     * limpava o "a atualizar", e o botão — que fica sem `clickable` enquanto atualiza — desaparecia
     * funcionalmente até a rede voltar sozinha. Foi exatamente o defeito que a 003 teve no ecrã da
     * tabela de rotas, repetido aqui.
     *
     * Verificar a rede antes de enfileirar só reduzia a probabilidade: a rede pode cair entre a
     * verificação e a execução. Sem restrição, o worker corre sempre, falha depressa e diz porquê —
     * a classe de erro deixa de existir em vez de ficar mais rara.
     */
    fun requestImmediateRefresh() {
        val request = OneTimeWorkRequestBuilder<SkyRefreshWorker>()
            .setInputData(workDataOf(SkyRefreshWorker.KEY_MANUAL to true))
            .build()

        workManager.enqueueUniqueWork(
            SkyRefreshWorker.ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
