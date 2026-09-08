package com.mysky.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Ponto único de agendamento da atualização da tabela de rotas.
 *
 * Par próprio, ao lado do `SkyWorkScheduler` e não dentro dele (AD-016). São dois tipos de trabalho
 * com garantias diferentes: aquele existe para impedir que dois agendamentos dupliquem sondagens de
 * posição; este é um pedido único e raro do utilizador. Juntá-los cumpriria a letra de uma regra e
 * trairia o seu propósito.
 *
 * O princípio IV, na versão 1.1.0 da constituição, é exatamente isto: cada tipo de trabalho tem um
 * único ponto de criação dos seus `WorkRequest`s.
 */
@Singleton
class RouteTableUpdateWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Enfileira a atualização.
     *
     * `KEEP` e não `REPLACE`: um segundo toque com uma descarga já em curso não a reinicia do zero
     * — o utilizador impaciente não pode custar duas transferências.
     *
     * @return o identificador do trabalho, para quem observa saber **qual** dos resultados no
     *   histórico é o desta tentativa. O WorkManager mantém os anteriores durante algum tempo, e
     *   sem isto uma falha antiga podia tapar um sucesso novo.
     */
    fun requestUpdate(): UUID {
        val request = OneTimeWorkRequestBuilder<RouteTableUpdateWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            RouteTableUpdateWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        return request.id
    }

    /** O estado do trabalho, para o repositório traduzir em linguagem de domínio. */
    fun observeWork(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(RouteTableUpdateWorker.WORK_NAME)
}
