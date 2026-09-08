package com.mysky.app.data.route

import androidx.work.Data
import androidx.work.WorkInfo
import com.mysky.app.data.local.FileTableReader
import com.mysky.app.data.local.RouteTableFixtures
import com.mysky.app.data.local.RouteTableSource
import com.mysky.app.data.route.NetworkAvailability
import com.mysky.app.domain.model.RouteUpdateError
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.worker.RouteTableUpdateWorkScheduler
import com.mysky.app.worker.RouteTableUpdateWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A fronteira onde o WorkManager para.
 *
 * O ecrã de definições nunca vê um `WorkInfo`; vê `RouteUpdateState`. Estes testes fixam a
 * tradução — e sobretudo que **cada causa de falha chega distinta**, porque "não tens rede" e "o
 * que veio não presta" pedem coisas diferentes ao utilizador.
 */
class RouteTableRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scheduler = mockk<RouteTableUpdateWorkScheduler>(relaxed = true)
    private val source = mockk<RouteTableSource>()
    private val network = mockk<NetworkAvailability>()
    private val work = MutableStateFlow<List<WorkInfo>>(emptyList())

    /** O id que o scheduler devolve, para os testes poderem falar do trabalho "desta tentativa". */
    private val requestId: UUID = UUID.randomUUID()

    private lateinit var table: File

    @Before
    fun setUp() {
        table = RouteTableFixtures.writeTable(
            file = folder.newFile(),
            routes = RouteTableFixtures.largeRoutes(150_000),
            generatedAtEpochSeconds = 1_757_289_600L,
        )
        every { scheduler.observeWork() } returns work
        every { scheduler.requestUpdate() } returns requestId
        every { source.open() } answers { FileTableReader(table) }
        every { network.isOnline() } returns true
    }

    private fun TestScope.repository() =
        RouteTableRepositoryImpl(scheduler, source, network, StandardTestDispatcher(testScheduler))

    private fun workInfo(
        state: WorkInfo.State,
        reason: String? = null,
        id: UUID = requestId,
    ): WorkInfo = WorkInfo(
        /* id = */ id,
        /* state = */ state,
        /* tags = */ emptySet(),
        /* outputData = */ reason?.let {
            Data.Builder().putString(RouteTableUpdateWorker.KEY_REASON, it).build()
        } ?: Data.EMPTY,
    )

    // --- A informação da tabela em uso ----------------------------------------------------------

    @Test
    fun `a data e a contagem vem do cabecalho do ficheiro em uso`() = runTest {
        val info = repository().tableInfo()

        assertEquals(1_757_289_600L, info?.generatedAtEpochSeconds)
        assertEquals(150_000, info?.routeCount)
    }

    @Test
    fun `sem tabela legivel a informacao vem nula em vez de rebentar`() = runTest {
        every { source.open() } returns null

        assertNull(repository().tableInfo())
    }

    // --- A tradução do estado -------------------------------------------------------------------

    @Test
    fun `sem trabalho nenhum o estado e parado`() = runTest {
        assertEquals(RouteUpdateState.Idle, repository().updateState.first())
    }

    @Test
    fun `trabalho enfileirado ou a correr e uma atualizacao em curso`() = runTest {
        val repository = repository()

        work.value = listOf(workInfo(WorkInfo.State.ENQUEUED))
        assertEquals(RouteUpdateState.InProgress, repository.updateState.first())

        work.value = listOf(workInfo(WorkInfo.State.RUNNING))
        assertEquals(RouteUpdateState.InProgress, repository.updateState.first())
    }

    @Test
    fun `sucesso traz a data e a contagem da tabela que ficou instalada`() = runTest {
        val repository = repository()
        repository.requestUpdate()
        // O worker escreve um ficheiro, não devolve dados: a contagem é lida da mesma fonte que o
        // ecrã usa para mostrar a data em uso, e por isso as duas nunca se podem contradizer.
        work.value = listOf(workInfo(WorkInfo.State.SUCCEEDED))

        val state = repository.updateState.first()

        assertEquals(RouteUpdateState.Success(1_757_289_600L, 150_000), state)
    }

    @Test
    fun `cada causa de falha chega distinta ao ecra`() = runTest {
        val repository = repository()
        repository.requestUpdate()

        work.value = listOf(workInfo(WorkInfo.State.FAILED, RouteTableUpdateWorker.REASON_UNREACHABLE))
        assertEquals(
            RouteUpdateState.Failure(RouteUpdateError.Unreachable),
            repository.updateState.first(),
        )

        work.value = listOf(workInfo(WorkInfo.State.FAILED, RouteTableUpdateWorker.REASON_INVALID))
        assertEquals(
            RouteUpdateState.Failure(RouteUpdateError.InvalidData),
            repository.updateState.first(),
        )

        work.value = listOf(workInfo(WorkInfo.State.FAILED))
        assertEquals(
            RouteUpdateState.Failure(RouteUpdateError.Unexpected),
            repository.updateState.first(),
        )
    }

    @Test
    fun `trabalho cancelado volta ao estado parado`() = runTest {
        val repository = repository()
        repository.requestUpdate()
        work.value = listOf(workInfo(WorkInfo.State.CANCELLED))

        assertEquals(RouteUpdateState.Idle, repository.updateState.first())
    }

    @Test
    fun `uma falha antiga no historico nao tapa o sucesso desta tentativa`() = runTest {
        // O WorkManager guarda o histórico do mesmo nome único durante algum tempo, e publica os
        // dois lado a lado. Escolher "o estado mais avançado" dava a pior resposta possível, porque
        // `FAILED` vem depois de `SUCCEEDED` na ordem do enumerado — o utilizador pedia a
        // atualização, ela corria bem, e o ecrã continuava a mostrar a falha da vez anterior.
        val repository = repository()
        repository.requestUpdate()
        val antiga = workInfo(
            state = WorkInfo.State.FAILED,
            reason = RouteTableUpdateWorker.REASON_UNREACHABLE,
            id = UUID.randomUUID(),
        )

        work.value = listOf(antiga, workInfo(WorkInfo.State.SUCCEEDED))

        assertEquals(
            RouteUpdateState.Success(1_757_289_600L, 150_000),
            repository.updateState.first(),
        )
    }

    @Test
    fun `um resultado de outra tentativa nao e apresentado como sendo desta`() = runTest {
        val repository = repository()

        work.value = listOf(workInfo(WorkInfo.State.SUCCEEDED, id = UUID.randomUUID()))

        // Sem pedido nosso, um resultado no histórico não é nosso para mostrar.
        assertEquals(RouteUpdateState.Idle, repository.updateState.first())
    }

    // --- Sem rede, dito de imediato -------------------------------------------------------------

    @Test
    fun `sem rede a falha e imediata e nenhum trabalho e enfileirado`() = runTest {
        // Com `NetworkType.CONNECTED`, enfileirar sem rede deixaria o trabalho em espera: o
        // WorkManager não o corre e não falha, e o ecrã ficaria em "A atualizar…" durante horas.
        every { network.isOnline() } returns false
        val repository = repository()

        repository.requestUpdate()

        assertEquals(
            RouteUpdateState.Failure(RouteUpdateError.NoConnection),
            repository.updateState.first(),
        )
        verify(exactly = 0) { scheduler.requestUpdate() }
    }

    @Test
    fun `voltar a pedir com rede limpa a falha anterior`() = runTest {
        every { network.isOnline() } returns false
        val repository = repository()
        repository.requestUpdate()

        every { network.isOnline() } returns true
        repository.requestUpdate()
        work.value = listOf(workInfo(WorkInfo.State.RUNNING))

        assertEquals(RouteUpdateState.InProgress, repository.updateState.first())
    }

    // --- Nada acontece sem o utilizador pedir (SC-010) -------------------------------------------

    @Test
    fun `construir o repositorio e ler a tabela nao enfileira trabalho nenhum`() = runTest {
        // Testado pela negativa de propósito: é o que se estraga com um `init` bem-intencionado, e
        // a app passaria a gastar dados do utilizador sozinha sem dar erro nenhum.
        val repository = repository()

        repository.tableInfo()
        repository.updateState.first()

        verify(exactly = 0) { scheduler.requestUpdate() }
    }

    @Test
    fun `so o pedido explicito enfileira trabalho`() = runTest {
        repository().requestUpdate()

        verify(exactly = 1) { scheduler.requestUpdate() }
    }
}
