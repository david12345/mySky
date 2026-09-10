package com.mysky.app.presentation.main

import app.cash.turbine.test
import com.mysky.app.LISBON
import com.mysky.app.MainDispatcherRule
import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.aircraft
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.overheadFlight
import com.mysky.app.presentation.sky.LoadPhase
import com.mysky.app.presentation.sky.FakeSettingsRepository
import com.mysky.app.presentation.sky.skySession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainViewModelStatesTest {

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

    private val flight = overheadFlight(aircraft = aircraft(icao24 = "aaa111"))

    private fun viewModel() = MainViewModel(
        skySession(observeSky, locationRepository, testDispatcher),
        locationRepository,
        FakeSettingsRepository(),
    )

    @Test
    fun `uma falha nao limpa a lista nem a marca temporal`() =
        runTest(mainDispatcherRule.testContext) {
            // FR-025: o utilizador estava a ler a lista; um erro de rede não lha pode apagar.
            coEvery { locationRepository.getCurrentLocation() } returns LISBON
            coEvery { observeSky(LISBON, any()) } returnsMany listOf(
                Result.success(listOf(flight)),
                Result.failure(SkyError.NoConnection),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.lastUpdatedEpochSeconds != null }

                advanceTimeBy(30_001)
                runCurrent()
                val afterFailure = awaitItemWhere { it.lastError != null }

                assertEquals(1, afterFailure.flights.size)
                assertEquals(NOW_EPOCH_SECONDS, afterFailure.lastUpdatedEpochSeconds)
                assertTrue(afterFailure.hasStaleResults)
                assertFalse(afterFailure.isBlockingError)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `um sucesso limpa o erro anterior`() = runTest(mainDispatcherRule.testContext) {
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } returnsMany listOf(
            Result.failure(SkyError.NoConnection),
            Result.success(listOf(flight)),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onScreenVisible()
            awaitItemWhere { it.lastError != null }

            advanceTimeBy(30_001)
            runCurrent()
            val recovered = awaitItemWhere { it.lastError == null && it.flights.isNotEmpty() }

            assertNull(recovered.lastError)
            assertFalse(recovered.hasStaleResults)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sem posicao o erro e LocationUnavailable e nao falha de rede`() =
        runTest(mainDispatcherRule.testContext) {
            // O repositório de localização devolve `null` em vez de falhar, por isso esta variante
            // só pode nascer aqui. Sem ela, "sem GPS" seria indistinguível de "sem Internet".
            coEvery { locationRepository.getCurrentLocation() } returns null
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                val state = awaitItemWhere { it.lastError != null }

                assertEquals(SkyError.LocationUnavailable, state.lastError)
                assertEquals(LoadPhase.Idle, state.phase)
                assertTrue(state.isBlockingError)

                cancelAndIgnoreRemainingEvents()
            }
            io.mockk.coVerify(exactly = 0) { observeSky(any(), any()) }
        }

    @Test
    fun `excesso de pedidos alonga a espera em vez de reintentar de imediato`() =
        runTest(mainDispatcherRule.testContext) {
            // FR-021: reintentar gastaria o orçamento diário exatamente quando ele já se esgotou.
            val calls = AtomicInteger(0)
            coEvery { locationRepository.getCurrentLocation() } returns LISBON
            coEvery { observeSky(LISBON, any()) } coAnswers {
                calls.incrementAndGet()
                Result.failure(SkyError.RateLimited(retryAfterSeconds = 90L))
            }
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.lastError is SkyError.RateLimited }
                assertEquals(1, calls.get())

                // O intervalo normal passa e não há nova tentativa: a espera é max(30 s, 90 s).
                advanceTimeBy(30_001)
                runCurrent()
                assertEquals(1, calls.get())

                advanceTimeBy(60_001)
                runCurrent()
                assertEquals(2, calls.get())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `excesso de pedidos sem tempo indicado usa o intervalo normal`() =
        runTest(mainDispatcherRule.testContext) {
            val calls = AtomicInteger(0)
            coEvery { locationRepository.getCurrentLocation() } returns LISBON
            coEvery { observeSky(LISBON, any()) } coAnswers {
                calls.incrementAndGet()
                Result.failure(SkyError.RateLimited(retryAfterSeconds = null))
            }
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.lastError != null }

                advanceTimeBy(30_001)
                runCurrent()
                assertEquals(2, calls.get())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `permissao revogada com o ecra aberto passa a recusada ao voltar`() =
        runTest(mainDispatcherRule.testContext) {
            coEvery { locationRepository.getCurrentLocation() } returns LISBON
            coEvery { observeSky(LISBON, any()) } returns Result.success(listOf(flight))
            every { locationRepository.hasLocationPermission() } returns true
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                awaitItemWhere { it.permission == PermissionState.Granted }

                // O utilizador foi às definições do sistema e revogou a permissão.
                every { locationRepository.hasLocationPermission() } returns false
                viewModel.onScreenVisible()

                assertEquals(PermissionState.Denied, awaitItemWhere { it.permission != PermissionState.Granted }.permission)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `voltar ao ecra sem nunca ter pedido mantem o estado por decidir`() =
        runTest(mainDispatcherRule.testContext) {
            // "Ainda não pediu" não é o mesmo que "recusou": o primeiro merece o rationale, o
            // segundo merece uma explicação de porque a app está sem dados.
            every { locationRepository.hasLocationPermission() } returns false
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onScreenVisible()
                runCurrent()

                expectNoEvents()
            }
        }

    @Test
    fun `recusa permanente sobrevive a uma reavaliacao da permissao`() =
        runTest(mainDispatcherRule.testContext) {
            every { locationRepository.hasLocationPermission() } returns false
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onPermissionResult(granted = false, canAskAgain = false)
                assertEquals(PermissionState.PermanentlyDenied, awaitItem().permission)

                viewModel.onScreenVisible()
                runCurrent()

                expectNoEvents()
            }
        }

    @Test
    fun `a fase nunca fica presa a carregar`() = runTest(mainDispatcherRule.testContext) {
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } returns Result.failure(SkyError.FlightServiceUnavailable(503))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onScreenVisible()

            assertEquals(LoadPhase.Idle, awaitItemWhere { it.lastError != null }.phase)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
