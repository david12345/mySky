package com.mysky.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mysky.app.LISBON
import com.mysky.app.MainDispatcherRule
import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.aircraft
import com.mysky.app.domain.model.Aircraft
import com.mysky.app.domain.model.FlightPresence
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.domain.usecase.TrackFlightPresenceUseCase
import com.mysky.app.overheadFlight
import com.mysky.app.presentation.navigation.MySkyRoutes
import com.mysky.app.presentation.sky.FakeSettingsRepository
import com.mysky.app.presentation.sky.skySession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

/**
 * As invariantes de `contracts/flight-detail-ui.md`.
 *
 * A que interessa mais é a terceira: um ciclo falhado **não** pode transformar-se em "a aeronave
 * saiu do teu céu". A mensagem seria plausível, o ecrã continuaria bonito, e o utilizador ficaria
 * a saber uma coisa falsa sempre que a rede oscilasse.
 */
class FlightDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher(mainDispatcherRule.scheduler)

    private val observeSky = mockk<ObserveSkyUseCase>()
    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    private var now = NOW_EPOCH_SECONDS

    private val alvo = overheadFlight(aircraft = aircraft(icao24 = "aaa111", callsign = "TAP1234"))
    private val outro = overheadFlight(aircraft = aircraft(icao24 = "bbb222", callsign = "RYR9999"))

    init {
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
    }

    private fun skyReturns(vararg flights: OverheadFlight) {
        coEvery { observeSky(LISBON, any()) } returns Result.success(flights.toList())
    }

    private fun viewModel(icao24: String? = "aaa111") = FlightDetailViewModel(
        savedStateHandle = SavedStateHandle(
            if (icao24 == null) emptyMap() else mapOf(FlightDetailViewModel.ARG_ICAO24 to icao24),
        ),
        skySession = skySession(
            observeSky = observeSky,
            locationRepository = locationRepository,
            dispatcher = testDispatcher,
            timeProvider = TimeProvider { now },
        ),
        trackFlightPresence = TrackFlightPresenceUseCase(),
        settingsRepository = FakeSettingsRepository(),
    )

    // --- Invariante 1: a aeronave certa --------------------------------------------------------

    @Test
    fun `o voo apresentado e o do identificador da rota`() = runTest(mainDispatcherRule.testContext) {
        skyReturns(outro, alvo)

        viewModel().uiState.test {
            val state = awaitItemWhere { it.flight != null }

            assertEquals("aaa111", state.flight?.aircraft?.icao24)
            assertEquals(FlightPresence.Current(alvo, NOW_EPOCH_SECONDS), state.presence)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rota sem aeronave nao rebenta e fica sem identificador`() =
        runTest(mainDispatcherRule.testContext) {
            skyReturns(alvo)

            viewModel(icao24 = null).uiState.test {
                val state = awaitItem()

                assertNull(state.icao24)
                assertNull(state.flight)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a rota do detalhe usa o mesmo nome de argumento que o ViewModel le`() {
        // Se um dos lados for renomeado sem o outro, o detalhe abre sempre sem aeronave — e nada
        // rebenta, o que é o pior tipo de falha.
        assertTrue(MySkyRoutes.FLIGHT_DETAIL.contains("{${FlightDetailViewModel.ARG_ICAO24}}"))
        assertEquals("flight/3c6444", MySkyRoutes.flightDetail("3c6444"))
    }

    // --- Invariantes 2 a 5: a presença ---------------------------------------------------------

    @Test
    fun `ceu observado sem a aeronave passa a saida do ceu`() =
        runTest(mainDispatcherRule.testContext) {
            skyReturns(alvo)

            viewModel().uiState.test {
                awaitItemWhere { it.presence is FlightPresence.Current }

                now += 30
                skyReturns(outro)
                advanceTimeBy(30_000)
                runCurrent()

                val state = awaitItemWhere { it.hasLeftSky }
                assertEquals(alvo, state.flight)
                assertEquals(NOW_EPOCH_SECONDS, state.lastSeenEpochSeconds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `um ciclo falhado nao anuncia que a aeronave saiu do ceu`() =
        runTest(mainDispatcherRule.testContext) {
            skyReturns(alvo)

            viewModel().uiState.test {
                awaitItemWhere { it.presence is FlightPresence.Current }

                coEvery { observeSky(LISBON, any()) } returns Result.failure(SkyError.NoConnection)
                advanceTimeBy(30_000)
                runCurrent()

                val state = awaitItemWhere { it.lastError != null }
                assertFalse(state.hasLeftSky)
                assertTrue(state.hasStaleData)
                assertEquals(alvo, state.flight)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reaparecer volta a apresentar dados atuais sem intervencao`() =
        runTest(mainDispatcherRule.testContext) {
            skyReturns(alvo)

            viewModel().uiState.test {
                awaitItemWhere { it.presence is FlightPresence.Current }

                now += 30
                skyReturns(outro)
                advanceTimeBy(30_000)
                runCurrent()
                awaitItemWhere { it.hasLeftSky }

                now += 30
                skyReturns(alvo)
                advanceTimeBy(30_000)
                runCurrent()

                assertFalse(awaitItemWhere { !it.hasLeftSky }.hasLeftSky)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `o instante da ultima observacao nao se mexe enquanto a aeronave estiver ausente`() =
        runTest(mainDispatcherRule.testContext) {
            skyReturns(alvo)
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItemWhere { it.presence is FlightPresence.Current }

                now += 30
                skyReturns(outro)
                advanceTimeBy(30_000)
                runCurrent()
                awaitItemWhere { it.hasLeftSky }

                now += 300
                advanceTimeBy(300_000)
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(NOW_EPOCH_SECONDS, viewModel.uiState.value.lastSeenEpochSeconds)
        }

    @Test
    fun `duas observacoes no mesmo segundo do relogio nao escondem a saida do ceu`() =
        runTest(mainDispatcherRule.testContext) {
            // A marca temporal tem granularidade de segundo, e dois ciclos podem cair no mesmo —
            // basta um refresh manual com resposta rápida. Se a identidade da observação fosse a
            // marca temporal, a segunda seria lida como "nada de novo" e uma aeronave que tivesse
            // saído do céu nesse segundo continuaria a ser mostrada como estando lá.
            skyReturns(alvo)
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItemWhere { it.presence is FlightPresence.Current }

                // Sem mexer em `now`: o segundo é o mesmo.
                skyReturns(outro)
                viewModel.onManualRefresh()
                runCurrent()

                assertTrue(awaitItemWhere { it.hasLeftSky }.hasLeftSky)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Invariante 6 e 8: falhas e rotações ---------------------------------------------------

    @Test
    fun `um erro logo no arranque ocupa o ecra e nada mais`() =
        runTest(mainDispatcherRule.testContext) {
            coEvery { observeSky(LISBON, any()) } returns Result.failure(SkyError.NoConnection)

            viewModel().uiState.test {
                val state = awaitItemWhere { it.lastError != null }

                assertTrue(state.isBlockingError)
                assertNull(state.flight)
                assertFalse(state.hasLeftSky)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uma nova subscricao dentro da janela nao repete o pedido nem esvazia o ecra`() =
        runTest(mainDispatcherRule.testContext) {
            skyReturns(alvo)
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItemWhere { it.flight != null }
                cancelAndIgnoreRemainingEvents()
            }

            // A rotação: o coletor sai e volta dentro dos 5 segundos.
            advanceTimeBy(1_000)
            viewModel.uiState.test {
                val state = awaitItem()

                assertEquals(alvo, state.flight)
                cancelAndIgnoreRemainingEvents()
            }
            io.mockk.coVerify(exactly = 1) { observeSky(LISBON, any()) }
        }

    // --- Campos ausentes (SC-006) ---------------------------------------------------------------

    @Test
    fun `campos ausentes chegam ao ecra como ausentes, nunca como zero`() =
        runTest(mainDispatcherRule.testContext) {
            // O ecrã omite cada campo com um `?.let`. O que este teste garante é o degrau anterior:
            // que nada no caminho até lá substitui uma ausência por um valor por omissão, que é
            // como um zero inventado apareceria com ar de medição.
            val despido = overheadFlight(
                aircraft = Aircraft(
                    icao24 = "aaa111",
                    callsign = null,
                    originCountry = null,
                    position = LISBON,
                    barometricAltitudeMeters = null,
                    geometricAltitudeMeters = 10_000.0,
                    groundSpeedMetersPerSecond = null,
                    headingDegrees = null,
                    verticalRateMetersPerSecond = null,
                ),
                airline = null,
            )
            skyReturns(despido)

            viewModel().uiState.test {
                val flight = awaitItemWhere { it.flight != null }.flight!!

                assertNull(flight.airline)
                assertNull(flight.aircraft.originCountry)
                assertNull(flight.aircraft.barometricAltitudeMeters)
                assertNull(flight.aircraft.groundSpeedMetersPerSecond)
                assertNull(flight.aircraft.headingDegrees)
                assertNull(flight.aircraft.verticalRateMetersPerSecond)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Consome emissões até uma satisfazer [predicate]: um StateFlow conflacia transições. */
    private suspend fun app.cash.turbine.ReceiveTurbine<FlightDetailUiState>.awaitItemWhere(
        predicate: (FlightDetailUiState) -> Boolean,
    ): FlightDetailUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
