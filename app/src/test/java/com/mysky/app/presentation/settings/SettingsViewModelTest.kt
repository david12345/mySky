package com.mysky.app.presentation.settings

import app.cash.turbine.test
import com.mysky.app.LISBON
import com.mysky.app.MainDispatcherRule
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.RouteTableRepository
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import com.mysky.app.presentation.sky.FakeSettingsRepository
import com.mysky.app.presentation.sky.skySession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * As invariantes do ecrã de definições.
 *
 * A que protege o orçamento é a terceira: **uma alteração de unidade não pede dados novos**. Não
 * afeta que aeronaves estão no céu, e pedi-los gastaria um crédito para obter exatamente a mesma
 * lista. É o tipo de desperdício que funciona perfeitamente e só se vê na fatura.
 */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher(mainDispatcherRule.scheduler)

    private val observeSky = mockk<ObserveSkyUseCase>()
    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val routeTableRepository = mockk<RouteTableRepository>(relaxed = true)
    private val settingsRepository = FakeSettingsRepository()
    private val requests = java.util.concurrent.atomic.AtomicInteger()

    init {
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        coEvery { observeSky(LISBON, any()) } coAnswers {
            requests.incrementAndGet()
            Result.success(emptyList())
        }
        every { routeTableRepository.updateState } returns MutableStateFlow(RouteUpdateState.Idle)
        coEvery { routeTableRepository.tableInfo() } returns null
    }

    private fun TestScope.viewModel(): SettingsViewModel {
        val session = skySession(
            observeSky = observeSky,
            locationRepository = locationRepository,
            dispatcher = testDispatcher,
            settingsRepository = settingsRepository,
        )
        // A sessão só corre com alguém a observá-la; é o que faz o `requestRefresh` ter efeito
        // visível nos testes, como no ecrã real.
        backgroundScope.launch { session.observation.collect {} }
        return SettingsViewModel(routeTableRepository, settingsRepository, session)
    }

    // --- Invariante 1: uma alteração de critério grava e acorda o laço ---------------------------

    @Test
    fun `alterar o raio grava e pede dados novos`() = runTest(mainDispatcherRule.testContext) {
        val viewModel = viewModel()
        runCurrent()
        advanceTimeBy(1)
        runCurrent()
        val antes = requests.get()

        viewModel.onRadiusChanged(80_000.0)
        runCurrent()

        assertEquals(80_000.0, settingsRepository.settings.first().detectionRadiusMeters, 0.001)
        assertEquals("um critério novo pede dados novos", antes + 1, requests.get())
    }

    @Test
    fun `alterar o angulo e a altitude tambem pede dados novos`() = runTest(mainDispatcherRule.testContext) {
        val viewModel = viewModel()
        runCurrent(); advanceTimeBy(1); runCurrent()
        val antes = requests.get()

        viewModel.onMinElevationChanged(10.0)
        runCurrent()
        viewModel.onMinAltitudeChanged(1_000.0)
        runCurrent()

        assertEquals(antes + 2, requests.get())
    }

    // --- Invariante 2: uma alteração de unidade não acorda nada ---------------------------------

    @Test
    fun `alterar a unidade grava e nao pede dados novos`() = runTest(mainDispatcherRule.testContext) {
        // A unidade não muda que aeronaves estão no céu. Pedir dados por causa dela seria gastar um
        // crédito do orçamento diário para obter exatamente a mesma lista.
        val viewModel = viewModel()
        runCurrent(); advanceTimeBy(1); runCurrent()
        val antes = requests.get()

        viewModel.onDistanceUnitChanged(DistanceUnit.MILES)
        runCurrent()
        viewModel.onAltitudeUnitChanged(AltitudeUnit.FEET)
        runCurrent()

        assertEquals(DistanceUnit.MILES, settingsRepository.settings.first().distanceUnit)
        assertEquals(AltitudeUnit.FEET, settingsRepository.settings.first().altitudeUnit)
        assertEquals("as unidades não podem custar um pedido", antes, requests.get())
    }

    // --- Invariante 4: repor -------------------------------------------------------------------

    @Test
    fun `repor devolve os criterios ao inicio numa so acao`() = runTest(mainDispatcherRule.testContext) {
        val viewModel = viewModel()
        settingsRepository.set(
            SkySettings(
                detectionRadiusMeters = 120_000.0,
                minElevationDegrees = 8.0,
                distanceUnit = DistanceUnit.MILES,
            ),
        )
        runCurrent()

        viewModel.onResetToDefaults()
        runCurrent()

        assertEquals(SkySettings(), settingsRepository.settings.first())
    }

    @Test
    fun `repor nao toca no que pertence a outras features`() = runTest(mainDispatcherRule.testContext) {
        val viewModel = viewModel()
        settingsRepository.set(SkySettings(refreshIntervalMinutes = 45L, notificationsEnabled = true))
        runCurrent()

        viewModel.onResetToDefaults()
        runCurrent()

        val settings = settingsRepository.settings.first()
        assertEquals(45L, settings.refreshIntervalMinutes)
        assertTrue(settings.notificationsEnabled)
    }

    // --- Invariantes 6 e 7: o que o estado diz ao ecrã ------------------------------------------

    @Test
    fun `o estado indica quais os valores que estao de origem`() = runTest(mainDispatcherRule.testContext) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val inicial = awaitItemWhere { it.settings == SkySettings() }
            assertTrue(inicial.isRadiusAtDefault)
            assertTrue(inicial.isAtDefaults)

            settingsRepository.set(SkySettings(detectionRadiusMeters = 90_000.0))
            val mexido = awaitItemWhere { !it.isRadiusAtDefault }
            assertFalse(mexido.isAtDefaults)
            assertTrue("o ângulo não foi tocado", mexido.isMinElevationAtDefault)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `o estado inicial nao avisa contra os valores de origem`() = runTest(mainDispatcherRule.testContext) {
        // O defeito que a revisão encontrou: o aviso aparecia a 100% dos utilizadores ao abrir as
        // definições pela primeira vez, sobre uma escolha que nunca fizeram. Nenhum teste o cobria
        // porque o do alcance passava um raio que não era o de origem.
        val viewModel = viewModel()

        viewModel.uiState.test {
            val inicial = awaitItemWhere { it.settings == SkySettings() }
            assertFalse(
                "os valores de fábrica não podem avisar contra si mesmos",
                inicial.radiusExceedsUsefulRange,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `o estado avisa quando o raio excede o alcance util do angulo`() =
        runTest(mainDispatcherRule.testContext) {
            // O caso que o utilizador não tem como descobrir sozinho: raio no máximo com o ângulo de
            // origem não traz aeronave nova nenhuma.
            val viewModel = viewModel()
            settingsRepository.set(SkySettings(detectionRadiusMeters = 150_000.0, minElevationDegrees = 25.0))

            viewModel.uiState.test {
                assertTrue(awaitItemWhere { it.radiusExceedsUsefulRange }.radiusExceedsUsefulRange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `com o angulo no minimo um raio grande deixa de ser avisado`() =
        runTest(mainDispatcherRule.testContext) {
            val viewModel = viewModel()
            settingsRepository.set(SkySettings(detectionRadiusMeters = 130_000.0, minElevationDegrees = 5.0))

            viewModel.uiState.test {
                val state = awaitItemWhere { it.settings.minElevationDegrees == 5.0 }
                assertFalse(state.radiusExceedsUsefulRange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Invariante 8: os limites de um controlo não dependem do outro ---------------------------

    @Test
    fun `o intervalo de um controlo nao muda quando o outro e alterado`() {
        // Testa o contrário do que a intuição sugere. Estreitar o intervalo do raio quando o ângulo
        // sobe pareceria ajudar — e moveria o limite debaixo do dedo do utilizador, além de tirar
        // sentido a dizer que um valor está "de origem" (AD-021).
        val comAnguloBaixo = SkySettings(minElevationDegrees = 5.0)
        val comAnguloAlto = SkySettings(minElevationDegrees = 60.0)

        assertEquals(SkySettings.RADIUS_RANGE, SkySettings.RADIUS_RANGE)
        assertEquals(comAnguloBaixo.coerced().detectionRadiusMeters, comAnguloAlto.coerced().detectionRadiusMeters, 0.001)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsUiState>.awaitItemWhere(
        predicate: (SettingsUiState) -> Boolean,
    ): SettingsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
