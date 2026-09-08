package com.mysky.app.data.route

import androidx.work.WorkInfo
import com.mysky.app.data.local.FileTableReader
import com.mysky.app.data.local.RouteTableFormat
import com.mysky.app.data.local.RouteTableSource
import com.mysky.app.di.IoDispatcher
import com.mysky.app.domain.model.RouteUpdateError
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.repository.RouteTableInfo
import com.mysky.app.domain.repository.RouteTableRepository
import com.mysky.app.worker.RouteTableUpdateWorkScheduler
import com.mysky.app.worker.RouteTableUpdateWorker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RouteTableRepository {

    override val updateState: Flow<RouteUpdateState> =
        scheduler.observeWork().map { infos -> stateOf(infos) }

    override suspend fun tableInfo(): RouteTableInfo? = withContext(dispatcher) {
        runCatching {
            source.open()?.use { reader ->
                RouteTableFormat.readHeader(reader)?.let {
                    RouteTableInfo(it.generatedAtEpochSeconds, it.recordCount)
                }
            }
        }.getOrNull()
    }

    override fun requestUpdate() = scheduler.requestUpdate()

    /**
     * Traduz o trabalho em estado de domínio.
     *
     * O sucesso não traz a contagem consigo — o worker escreve um ficheiro, não devolve dados — por
     * isso é lida do cabeçalho da tabela que ficou instalada. É a mesma fonte que o ecrã usa para
     * mostrar a data em uso, o que garante que as duas coisas nunca se contradizem.
     */
    private fun stateOf(infos: List<WorkInfo>): RouteUpdateState {
        val info = infos.maxByOrNull { it.state.ordinal } ?: return RouteUpdateState.Idle
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                RouteUpdateState.InProgress
            WorkInfo.State.SUCCEEDED -> succeeded()
            WorkInfo.State.FAILED -> RouteUpdateState.Failure(errorOf(info))
            WorkInfo.State.CANCELLED -> RouteUpdateState.Idle
        }
    }

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
