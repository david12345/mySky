package com.mysky.app.data.route

import androidx.work.WorkInfo
import com.mysky.app.data.local.RouteTableFormat
import com.mysky.app.data.local.RouteTableSource
import com.mysky.app.di.IoDispatcher
import com.mysky.app.domain.model.RouteUpdateError
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.repository.RouteTableInfo
import com.mysky.app.domain.repository.RouteTableRepository
import com.mysky.app.worker.RouteTableUpdateWorkScheduler
import com.mysky.app.worker.RouteTableUpdateWorker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * A fronteira onde o WorkManager para.
 *
 * Acima daqui só há [RouteUpdateState]: o ecrã de definições não sabe o que é um `WorkInfo`, tal
 * como o ecrã principal não sabe o que é uma exceção de rede (AD-010, AD-016, AD-017). Se um dia a
 * atualização deixar de passar pelo WorkManager, nada acima desta classe precisa de mudar.
 */
@Singleton
class RouteTableRepositoryImpl @Inject constructor(
    private val scheduler: RouteTableUpdateWorkScheduler,
    private val source: RouteTableSource,
    private val network: NetworkAvailability,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RouteTableRepository {

    /**
     * O trabalho desta tentativa.
     *
     * O WorkManager guarda o histórico do mesmo nome único durante algum tempo, e a lista que
     * publica pode trazer o resultado antigo ao lado do novo. Sem saber qual é o desta tentativa,
     * uma falha anterior podia tapar um sucesso — que é precisamente o que o utilizador está a
     * olhar para saber.
     */
    private val currentRequest = MutableStateFlow<UUID?>(null)

    /**
     * Falha produzida sem chegar a enfileirar trabalho — hoje, só a ausência de rede.
     *
     * Fica por cima do estado do WorkManager até ao pedido seguinte, porque nesse caso não há
     * `WorkInfo` nenhum a contar a história.
     */
    private val localFailure = MutableStateFlow<RouteUpdateState.Failure?>(null)

    override val updateState: Flow<RouteUpdateState> =
        combine(scheduler.observeWork(), currentRequest, localFailure) { infos, request, failure ->
            failure ?: stateOf(infos, request)
        }

    override suspend fun tableInfo(): RouteTableInfo? = withContext(dispatcher) {
        runCatching {
            source.open()?.use { reader ->
                RouteTableFormat.readHeader(reader)?.let {
                    RouteTableInfo(it.generatedAtEpochSeconds, it.recordCount)
                }
            }
        }.getOrNull()
    }

    /**
     * A verificação de rede acontece **antes** de enfileirar, e é o que faz "não tens rede" chegar
     * ao ecrã de imediato.
     *
     * Sem ela, o `WorkRequest` — que exige `NetworkType.CONNECTED` — ficaria simplesmente em espera:
     * o WorkManager não corre o trabalho e não falha, e o utilizador ficaria a olhar para "A
     * atualizar…" até ligar os dados, minutos ou horas depois. A restrição no pedido mantém-se, mas
     * como rede de segurança para uma ligação que caia a meio.
     */
    override fun requestUpdate() {
        if (!network.isOnline()) {
            localFailure.value = RouteUpdateState.Failure(RouteUpdateError.NoConnection)
            return
        }
        localFailure.value = null
        currentRequest.value = scheduler.requestUpdate()
    }

    /**
     * Traduz o trabalho em estado de domínio.
     *
     * Um trabalho por terminar ganha sempre: é inequívoco, e cobre o caso de o processo ter morrido
     * e reiniciado a meio de uma descarga, em que já não sabemos qual era o nosso pedido. Entre os
     * terminados, só conta o desta tentativa — escolher pelo "estado mais avançado", como uma
     * primeira versão fazia, dava a pior resposta possível, porque `FAILED` vem depois de
     * `SUCCEEDED` na ordem do enumerado e uma falha antiga tapava o sucesso novo.
     */
    private fun stateOf(infos: List<WorkInfo>, request: UUID?): RouteUpdateState {
        if (infos.any { !it.state.isFinished }) return RouteUpdateState.InProgress

        val mine = infos.firstOrNull { it.id == request } ?: return RouteUpdateState.Idle
        return when (mine.state) {
            WorkInfo.State.SUCCEEDED -> succeeded()
            WorkInfo.State.FAILED -> RouteUpdateState.Failure(errorOf(mine))
            else -> RouteUpdateState.Idle
        }
    }

    /**
     * O worker escreve um ficheiro e não devolve dados, por isso a contagem vem do cabeçalho da
     * tabela instalada — a mesma fonte que o ecrã usa para mostrar a data em uso, o que garante que
     * as duas coisas nunca se contradizem.
     */
    private fun succeeded(): RouteUpdateState {
        val header = runCatching {
            source.open()?.use { RouteTableFormat.readHeader(it) }
        }.getOrNull() ?: return RouteUpdateState.Failure(RouteUpdateError.Unexpected)

        return RouteUpdateState.Success(header.generatedAtEpochSeconds, header.recordCount)
    }

    private fun errorOf(info: WorkInfo): RouteUpdateError =
        when (info.outputData.getString(RouteTableUpdateWorker.KEY_REASON)) {
            RouteTableUpdateWorker.REASON_UNREACHABLE -> RouteUpdateError.Unreachable
            RouteTableUpdateWorker.REASON_INVALID -> RouteUpdateError.InvalidData
            else -> RouteUpdateError.Unexpected
        }
}
