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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Invariante 9 do contrato de UI: uma rotação de ecrã destrói e recria a activity, mas não pode
 * repetir um pedido já concluído nem perder o que estava no ecrã (FR-026). É a janela de 5 s do
 * `WhileSubscribed` que o garante — este teste existe para que encurtá-la seja um erro visível.
 */
class MainViewModelConfigChangeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeSky = mockk<ObserveSkyUseCase>()
    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    private val flight = overheadFlight(aircraft = aircraft(icao24 = "aaa111"))

    private fun viewModel() = MainViewModel(
        observeSky,
        locationRepository,
        TimeProvider { NOW_EPOCH_SECONDS },
    )

    @Test
    fun `nova subscricao dentro da janela reaproveita o estado e nao pede de novo`() =
        runTest(mainDispatcherRule.testContext) {
            coEvery { locationRepository.getCurrentLocation() } returns LISBON
            coEvery { observeSky(LISBON, any()) } returns Result.success(listOf(flight))
            val viewModel = viewModel()

            // Primeira "activity": carrega e mostra a lista.
            viewModel.uiState.test {
                awaitItem()
                viewModel.onPermissionResult(granted = true, canAskAgain = true)
                val loaded = awaitItemWhere { it.lastUpdatedEpochSeconds != null }
                assertEquals(1, loaded.flights.size)
            }

            // A rotação: coletor desaparece e volta dentro dos 5 s.
            advanceTimeBy(1_000)
            runCurrent()

            viewModel.uiState.test {
                val restored = awaitItem()

                assertEquals(1, restored.flights.size)
                assertEquals(NOW_EPOCH_SECONDS, restored.lastUpdatedEpochSeconds)
                assertNull(restored.lastError)
                assertEquals(PermissionState.Granted, restored.permission)
            }

            coVerify(exactly = 1) { locationRepository.getCurrentLocation() }
        }

    @Test
    fun `a rotacao preserva tambem o erro da ultima tentativa`() =
        runTest(mainDispatcherRule.testContext) {
            coEvery { locationRepository.getCurrentLocation() } returns null
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onPermissionResult(granted = true, canAskAgain = true)
                awaitItemWhere { it.lastError != null }
            }

            advanceTimeBy(1_000)
            runCurrent()

            viewModel.uiState.test {
                assertEquals(
                    com.mysky.app.domain.model.SkyError.LocationUnavailable,
                    awaitItem().lastError,
                )
            }
        }
}
