package com.mysky.app.presentation.sky

import com.mysky.app.LISBON
import com.mysky.app.aircraft
import com.mysky.app.domain.model.OverheadCriteria
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.overheadFlight
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * As nove invariantes de `contracts/sky-session.md`.
 *
 * A razão de ser desta classe é a primeira: um pedido por ciclo por mais ecrãs que estejam a
 * observar. É também a que se parte sem deixar rasto — o ecrã continua correto e só a fatura da
 * fonte de dados denuncia o problema.
 */
class SkySessionTest {

    private val observeSky = mockk<ObserveSkyUseCase>()
    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val lifecycle = FakeRetainedLifecycle()
    private val requests = AtomicInteger()

    private val flight = overheadFlight(aircraft = aircraft(icao24 = "aaa111"))

    init {
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } coAnswers {
            requests.incrementAndGet()
            Result.success(listOf(flight))
        }
    }

    private fun TestScope.session(
        settings: SettingsRepository = FakeSettingsRepository(),
    ) = skySession(
        observeSky = observeSky,
        locationRepository = locationRepository,
        dispatcher = StandardTestDispatcher(testScheduler),
        lifecycle = lifecycle,
        settingsRepository = settings,
    )

    /** Deixa o primeiro ciclo correr até ao fim, sem consumir nada do intervalo de 30 s. */
    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(1)
        runCurrent()
    }

    // --- Invariante 1 e 2: um pedido, dois ecrãs, a mesma observação --------------------------

    @Test
    fun `dois observadores em simultaneo geram um so pedido por ciclo`() = runTest {
        val session = session()
        backgroundScope.launch { session.observation.collect {} }
        backgroundScope.launch { session.observation.collect {} }

        settle()
        assertEquals(1, requests.get())

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, requests.get())
    }

    @Test
    fun `os dois observadores veem a mesma observacao`() = runTest {
        val session = session()
        val primeiro = mutableListOf<SkyObservation>()
        val segundo = mutableListOf<SkyObservation>()
        backgroundScope.launch { session.observation.collect { primeiro += it } }
        backgroundScope.launch { session.observation.collect { segundo += it } }

        settle()

        assertEquals(primeiro, segundo)
        assertEquals(listOf(flight), primeiro.last().flights)
    }

    // --- Invariante 3: a transição lista -> detalhe -------------------------------------------

    @Test
    fun `passar da lista para o detalhe nao reinicia o laco nem repete o pedido`() = runTest {
        val session = session()
        val lista = backgroundScope.launch { session.observation.collect {} }
        settle()
        assertEquals(1, requests.get())

        // O detalhe entra antes de a lista sair: a contagem passa por 1 -> 2 -> 1 sem chegar a zero.
        backgroundScope.launch { session.observation.collect {} }
        runCurrent()
        lista.cancel()
        advanceTimeBy(2_000)
        runCurrent()

        // Se o laço tivesse reiniciado na transição, haveria aqui um pedido novo.
        assertEquals(1, requests.get())

        // E o relógio do ciclo seguiu o seu ritmo, em vez de recomeçar do zero.
        advanceTimeBy(28_000)
        runCurrent()
        assertEquals(2, requests.get())
    }

    // --- Invariante 4: sem ninguém a ver, não há trabalho -------------------------------------

    @Test
    fun `sem observadores o laco para e deixa de haver pedidos`() = runTest {
        val session = session()
        val ecra = backgroundScope.launch { session.observation.collect {} }
        settle()
        ecra.cancel()

        // Passada a janela de 5 s que cobre rotações e navegação, nada mais acontece.
        advanceTimeBy(10_000)
        runCurrent()
        val depoisDeParar = requests.get()

        advanceTimeBy(120_000)
        runCurrent()

        assertEquals(depoisDeParar, requests.get())
    }

    // --- Invariante 5: uma falha não apaga o que estava no ecrã -------------------------------

    @Test
    fun `uma falha nao limpa os voos nem a marca temporal`() = runTest {
        val session = session()
        backgroundScope.launch { session.observation.collect {} }
        settle()

        coEvery { observeSky(LISBON, any()) } returns Result.failure(SkyError.NoConnection)
        advanceTimeBy(30_000)
        runCurrent()

        val observation = session.observation.value
        assertEquals(listOf(flight), observation.flights)
        assertNotNull(observation.lastUpdatedEpochSeconds)
        assertEquals(SkyError.NoConnection, observation.lastError)
        assertEquals(LoadPhase.Idle, observation.phase)
    }

    // --- Invariante 6: um 429 alonga a espera --------------------------------------------------

    @Test
    fun `excesso de pedidos alonga a espera em vez de reintentar de imediato`() = runTest {
        coEvery { observeSky(LISBON, any()) } coAnswers {
            requests.incrementAndGet()
            Result.failure(SkyError.RateLimited(retryAfterSeconds = 90L))
        }
        val session = session()
        backgroundScope.launch { session.observation.collect {} }
        settle()
        assertEquals(1, requests.get())

        // Ao fim do intervalo normal ainda não pode ter havido nova tentativa.
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, requests.get())

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, requests.get())
    }

    // --- Invariantes 7 e 8: o pedido manual ----------------------------------------------------

    @Test
    fun `um pedido manual renova logo e reinicia o relogio`() = runTest {
        val session = session()
        backgroundScope.launch { session.observation.collect {} }
        settle()

        advanceTimeBy(20_000)
        session.requestRefresh()
        runCurrent()
        assertEquals(2, requests.get())

        // Os 10 s que faltavam ao ciclo antigo não podem produzir um pedido: o relógio recomeçou.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, requests.get())

        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(3, requests.get())
    }

    @Test
    fun `varios toques durante um ciclo em curso valem por um`() = runTest {
        // A conflação só funde o que chega com o laço ocupado — que é exatamente quando o
        // utilizador insiste. Com o laço à espera, um toque é servido de imediato, como deve ser.
        val gate = CompletableDeferred<Unit>()
        coEvery { observeSky(LISBON, any()) } coAnswers {
            requests.incrementAndGet()
            gate.await()
            Result.success(listOf(flight))
        }
        val session = session()
        backgroundScope.launch { session.observation.collect {} }
        runCurrent()
        assertEquals(1, requests.get())

        session.requestRefresh()
        session.requestRefresh()
        session.requestRefresh()
        runCurrent()

        // Nenhum pedido concorrente: o ciclo em curso não é interrompido nem duplicado.
        assertEquals(1, requests.get())

        gate.complete(Unit)
        runCurrent()
        assertEquals(2, requests.get())

        // E nenhum toque ficou por consumir à espera de produzir um ciclo extra.
        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(2, requests.get())
    }

    // --- Invariante 9: o escopo é cancelado com a Activity -------------------------------------

    @Test
    fun `o fim do ciclo de vida cancela o laco`() = runTest {
        val session = session()
        backgroundScope.launch { session.observation.collect {} }
        settle()
        val antes = requests.get()

        lifecycle.clear()
        advanceTimeBy(120_000)
        runCurrent()

        assertEquals(antes, requests.get())
    }

    // --- Os critérios vêm das preferências, um snapshot por ciclo (AD-018) ----------------------

    @Test
    fun `cada ciclo usa os criterios guardados no momento em que comeca`() = runTest {
        val settings = FakeSettingsRepository()
        val criterios = mutableListOf<OverheadCriteria>()
        coEvery { observeSky(LISBON, capture(criterios)) } coAnswers {
            requests.incrementAndGet()
            Result.success(listOf(flight))
        }
        val session = session(settings)
        backgroundScope.launch { session.observation.collect {} }
        settle()

        settings.set(SkySettings(detectionRadiusMeters = 90_000.0))
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(2, criterios.size)
        assertEquals(30_000.0, criterios[0].maxHorizontalDistanceMeters, 0.001)
        assertEquals("o ciclo seguinte usa o critério novo", 90_000.0, criterios[1].maxHorizontalDistanceMeters, 0.001)
    }

    @Test
    fun `uma alteracao a meio de um ciclo so aparece no ciclo seguinte`() = runTest {
        // É isto que o FR-017 exige, e sai da forma dos dados: o ciclo trabalha com o snapshot que
        // leu no início, e os critérios são passados por valor ao caso de uso. Uma lista com
        // critérios misturados é estruturalmente impossível — não há nada para alguém se lembrar de
        // fazer.
        val settings = FakeSettingsRepository()
        val gate = CompletableDeferred<Unit>()
        val criterios = mutableListOf<OverheadCriteria>()
        coEvery { observeSky(LISBON, capture(criterios)) } coAnswers {
            requests.incrementAndGet()
            if (requests.get() == 1) gate.await()
            Result.success(listOf(flight))
        }
        val session = session(settings)
        backgroundScope.launch { session.observation.collect {} }
        runCurrent()

        // O primeiro ciclo está preso no gate, já com o seu snapshot lido.
        settings.set(SkySettings(detectionRadiusMeters = 120_000.0))
        runCurrent()
        assertEquals(30_000.0, criterios[0].maxHorizontalDistanceMeters, 0.001)

        gate.complete(Unit)
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(120_000.0, criterios[1].maxHorizontalDistanceMeters, 0.001)
    }

    @Test
    fun `os extremos permitidos continuam a produzir observacoes`() = runTest {
        // SC-004: nenhuma combinação permitida pode parar a app.
        val settings = FakeSettingsRepository(
            SkySettings(detectionRadiusMeters = 150_000.0, minElevationDegrees = 5.0),
        )
        val session = session(settings)
        backgroundScope.launch { session.observation.collect {} }
        settle()

        assertEquals(1, requests.get())

        settings.set(SkySettings(detectionRadiusMeters = 5_000.0, minElevationDegrees = 60.0))
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(2, requests.get())
    }
}
