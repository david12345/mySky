package com.mysky.app.domain.usecase

import com.mysky.app.LISBON
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.time.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O ciclo partilhado pelo ecrã e pelo trabalho de fundo.
 *
 * O teste que mais importa é o segundo: **sem permissão não se gasta uma consulta**. É invisível em
 * uso — a app comporta-se igual — e custaria orçamento diário a quem tem o widget no ecrã e a
 * localização desligada.
 */
class RunSkyCycleUseCaseTest {

    private val locationRepository = mockk<LocationRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val observeSky = mockk<ObserveSkyUseCase>()
    private val timeProvider = mockk<TimeProvider>()

    private val useCase = RunSkyCycleUseCase(
        locationRepository, settingsRepository, observeSky, timeProvider,
    )

    private fun comPermissao(settings: SkySettings = SkySettings()) {
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.getCurrentLocation() } returns LISBON
        every { settingsRepository.settings } returns flowOf(settings)
        every { timeProvider.nowEpochSeconds() } returns 1_700_000_000L
    }

    @Test
    fun `um ciclo bem sucedido devolve os voos e o instante`() = runTest {
        comPermissao()
        coEvery { observeSky(LISBON, any()) } returns Result.success(emptyList())

        val resultado = useCase()

        assertTrue(resultado is SkyCycleResult.Success)
        assertEquals(1_700_000_000L, (resultado as SkyCycleResult.Success).observedAtEpochSeconds)
    }

    @Test
    fun `sem permissao nao vai a rede nem gasta consulta`() = runTest {
        every { locationRepository.hasLocationPermission() } returns false

        val resultado = useCase()

        assertEquals(SkyCycleResult.NoPermission, resultado)
        coVerify(exactly = 0) { observeSky(any(), any()) }
        coVerify(exactly = 0) { locationRepository.getCurrentLocation() }
    }

    @Test
    fun `localizacao indisponivel com permissao dada e distinta de nao ter permissao`() = runTest {
        // Duas causas diferentes com remédios diferentes: uma pede-se ao utilizador, a outra espera-se.
        // Confundi-las levaria o widget a pedir permissão que já tem.
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.getCurrentLocation() } returns null

        val resultado = useCase()

        assertEquals(SkyError.LocationUnavailable, (resultado as SkyCycleResult.Failure).error)
    }

    @Test
    fun `um erro do ceu sai como falha e nao por lancamento`() = runTest {
        comPermissao()
        coEvery { observeSky(LISBON, any()) } returns Result.failure(SkyError.NoConnection)

        assertEquals(SkyError.NoConnection, (useCase() as SkyCycleResult.Failure).error)
    }

    @Test
    fun `nada sai daqui por lancamento, nem o que e culpa de um colaborador`() = runTest {
        // A promessa de "nunca lança" é desta função e não pode depender de todos os colaboradores
        // cumprirem a deles. Um worker que rebentasse aqui morria sem gravar estado nenhum.
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.getCurrentLocation() } throws IllegalStateException("boom")

        val resultado = useCase()

        assertTrue((resultado as SkyCycleResult.Failure).error is SkyError.Unexpected)
    }

    @Test
    fun `os criterios sao lidos uma vez, no inicio do ciclo`() = runTest {
        // AD-018: um snapshot imutável por ciclo. Se fossem lidos por aeronave, ou a meio, uma
        // alteração durante o ciclo produziria uma lista com critérios misturados.
        val settings = SkySettings(detectionRadiusMeters = 80_000.0)
        comPermissao(settings)
        coEvery { observeSky(LISBON, settings.toCriteria()) } returns Result.success(emptyList())

        useCase()

        coVerify(exactly = 1) { observeSky(LISBON, settings.toCriteria()) }
    }
}
