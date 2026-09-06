package com.mysky.app.presentation.main

import app.cash.turbine.test
import com.mysky.app.LISBON
import com.mysky.app.MainDispatcherRule
import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.overheadFlight
import com.mysky.app.aircraft
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeSky = mockk<ObserveSkyUseCase>()
    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val timeProvider = TimeProvider { NOW_EPOCH_SECONDS }

    private val highFlight = overheadFlight(aircraft = aircraft(icao24 = "alto"), elevationDegrees = 80.0)
    private val lowFlight = overheadFlight(aircraft = aircraft(icao24 = "baixo"), elevationDegrees = 30.0)

    private fun viewModel() = MainViewModel(observeSky, locationRepository, timeProvider)

    @Test
    fun `estado inicial nao tem permissao nem trabalho em curso`() = runTest(mainDispatcherRule.testContext) {
        viewModel().uiState.test {
            val initial = awaitItem()

            assertEquals(PermissionState.Unknown, initial.permission)
            assertEquals(LoadPhase.Idle, initial.phase)
            assertTrue(initial.flights.isEmpty())
            assertNull(initial.lastUpdatedEpochSeconds)
            assertNull(initial.lastError)
        }
    }

    @Test
    fun `sem permissao nao se pede localizacao nem voos`() = runTest(mainDispatcherRule.testContext) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPermissionResult(granted = false, canAskAgain = true)

            assertEquals(PermissionState.Denied, awaitItem().permission)
            expectNoEvents()
        }
        io.mockk.coVerify(exactly = 0) { locationRepository.getCurrentLocation() }
    }

    @Test
    fun `a carga passa por localizar e por carregar antes de ficar parada`() =
        runTest(mainDispatcherRule.testContext) {
            // As duas fases são travadas à vez para poderem ser observadas: um StateFlow conflaciona
            // e engoliria a transição se as duas chamadas devolvessem logo.
            val locationGate = CompletableDeferred<Unit>()
            val flightsGate = CompletableDeferred<Unit>()
            coEvery { locationRepository.getCurrentLocation() } coAnswers {
                locationGate.await()
                LISBON
            }
            coEvery { observeSky(LISBON, any()) } coAnswers {
                flightsGate.await()
                Result.success(listOf(highFlight))
            }
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onPermissionResult(granted = true, canAskAgain = true)

                // Espera-se por condição e não por emissão: um StateFlow conflacia, e conceder a
                // permissão e começar a localizar acontecem antes de o coletor correr uma vez.
                val locating = awaitItemWhere { it.phase == LoadPhase.LocatingUser }
                assertEquals(PermissionState.Granted, locating.permission)

                locationGate.complete(Unit)
                awaitItemWhere { it.phase == LoadPhase.LoadingFlights }

                flightsGate.complete(Unit)
                val idle = awaitItemWhere { it.phase == LoadPhase.Idle }
                assertEquals(listOf("alto"), idle.flights.map { it.aircraft.icao24 })
            }
        }

    @Test
    fun `sucesso preenche os voos e a marca temporal`() = runTest(mainDispatcherRule.testContext) {
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } returns Result.success(listOf(highFlight, lowFlight))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPermissionResult(granted = true, canAskAgain = true)

            val loaded = awaitItemWhere { it.phase == LoadPhase.Idle && it.flights.isNotEmpty() }

            assertEquals(2, loaded.flights.size)
            assertEquals(NOW_EPOCH_SECONDS, loaded.lastUpdatedEpochSeconds)
            assertNull(loaded.lastError)
        }
    }

    @Test
    fun `a lista chega ordenada por elevacao decrescente`() = runTest(mainDispatcherRule.testContext) {
        // A ordenação é do caso de uso; aqui garante-se que o ViewModel não a desfaz.
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } returns Result.success(listOf(highFlight, lowFlight))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPermissionResult(granted = true, canAskAgain = true)

            val loaded = awaitItemWhere { it.flights.isNotEmpty() }

            assertEquals(listOf("alto", "baixo"), loaded.flights.map { it.aircraft.icao24 })
        }
    }

    @Test
    fun `permissao concedida arranca o ciclo`() = runTest(mainDispatcherRule.testContext) {
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } returns Result.success(emptyList())
        every { locationRepository.hasLocationPermission() } returns true
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onScreenVisible()

            val loaded = awaitItemWhere { it.lastUpdatedEpochSeconds != null }

            assertEquals(PermissionState.Granted, loaded.permission)
            assertTrue(loaded.isSkyEmpty)
        }
    }
}

/** Consome emissões até uma satisfazer [predicate]. Evita contar transições intermédias à mão. */
internal suspend fun app.cash.turbine.ReceiveTurbine<MainUiState>.awaitItemWhere(
    predicate: (MainUiState) -> Boolean,
): MainUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
