package com.mysky.app.presentation.main

import app.cash.turbine.test
import com.mysky.app.LISBON
import com.mysky.app.MainDispatcherRule
import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.aircraft
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.overheadFlight
import com.mysky.app.presentation.sky.LoadPhase
import com.mysky.app.presentation.sky.skySession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * O laço de atualização em tempo virtual: 30 segundos passam instantaneamente e as asserções são
 * sobre chamadas e emissões, não sobre relógios reais.
 */
class MainViewModelRefreshLoopTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Mesmo scheduler do `runTest`: sem isto o tempo virtual da sessão seria outro. */
    private val testDispatcher = StandardTestDispatcher(mainDispatcherRule.scheduler)

    private val observeSky = mockk<ObserveSkyUseCase>()
    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    init {
        // A sessão pergunta a permissão ao repositório uma vez por ciclo (AD-011), em vez de ser
        // comandada pelo estado do ViewModel. Os testes que exercitam o laço têm de o dizer aqui;
        // os que testam a ausência de permissão sobrepõem-se a isto explicitamente.
        every { locationRepository.hasLocationPermission() } returns true
    }

    private val calls = AtomicInteger(0)

    private fun viewModel() = MainViewModel(
        skySession(observeSky, locationRepository, testDispatcher),
        locationRepository,
    )

    private fun countingSky() {
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } coAnswers {
            calls.incrementAndGet()
            Result.success(listOf(overheadFlight(aircraft = aircraft(icao24 = "aaa111"))))
        }
    }

    @Test
    fun `a lista renova-se a cada trinta segundos`() = runTest(mainDispatcherRule.testContext) {
        countingSky()
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onScreenVisible()
            awaitItemWhere { it.lastUpdatedEpochSeconds != null }
            assertEquals(1, calls.get())

            advanceTimeBy(30_001)
            runCurrent()
            assertEquals(2, calls.get())

            advanceTimeBy(30_001)
            runCurrent()
            assertEquals(3, calls.get())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh manual reinicia o relogio dos trinta segundos`() =
        runTest(mainDispatcherRule.testContext) {
            countingSky()
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.lastUpdatedEpochSeconds != null }

                advanceTimeBy(20_000)
                runCurrent()
                viewModel.onManualRefresh()
                runCurrent()
                assertEquals("o pedido manual atualiza logo", 2, calls.get())

                // 20 s depois do manual: se o relógio não tivesse reiniciado, o automático dos
                // 30 s originais já teria disparado aqui.
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(2, calls.get())

                advanceTimeBy(10_001)
                runCurrent()
                assertEquals(3, calls.get())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh manual durante um automatico nao gera pedido concorrente`() =
        runTest(mainDispatcherRule.testContext) {
            val gate = CompletableDeferred<Unit>()
            coEvery { locationRepository.getCurrentLocation() } returns LISBON
            coEvery { observeSky(LISBON, any()) } coAnswers {
                calls.incrementAndGet()
                gate.await()
                Result.success(emptyList())
            }
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.phase == LoadPhase.LoadingFlights }

                // Pedido em curso e travado: insistir não pode produzir um segundo.
                repeat(5) { viewModel.onManualRefresh() }
                runCurrent()
                assertEquals(1, calls.get())

                gate.complete(Unit)
                runCurrent()
                // O pedido manual que ficou em espera corre a seguir, sequencialmente.
                assertEquals(2, calls.get())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sem subscritores o laco para e deixa de haver rede e localizacao`() =
        runTest(mainDispatcherRule.testContext) {
            countingSky()
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.lastUpdatedEpochSeconds != null }
                assertEquals(1, calls.get())
                cancelAndIgnoreRemainingEvents()
            }

            // Muito para lá dos 5 s da janela e dos 60 s de SC-007.
            advanceTimeBy(120_000)
            runCurrent()

            assertEquals("o laço continuou a correr em segundo plano", 1, calls.get())
            coVerify(exactly = 1) { locationRepository.getCurrentLocation() }
        }
}
