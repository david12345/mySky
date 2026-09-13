package com.mysky.app.domain.usecase

import com.mysky.app.aircraft
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.NotificationPermission
import com.mysky.app.domain.repository.SightingRepository
import com.mysky.app.overheadFlight
import com.mysky.app.presentation.sky.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As sete invariantes do contrato, e a ordem por que são verificadas.
 *
 * O primeiro teste é o que protege a bateria de toda a gente: as notificações estão desligadas por
 * omissão, e sem a verificação do interruptor **primeiro**, cada ciclo de cada utilizador faria uma
 * consulta à base de dados para concluir que não tinha nada a fazer.
 */
class DecideOverheadNotificationUseCaseTest {

    private val sightingRepository = mockk<SightingRepository>(relaxed = true)
    private var permissionGranted = true
    private val notificationPermission = NotificationPermission { permissionGranted }

    private fun useCase(settings: SkySettings) = DecideOverheadNotificationUseCase(
        settingsRepository = FakeSettingsRepository(settings),
        sightingRepository = sightingRepository,
        notificationPermission = notificationPermission,
    )

    private val ligadas = SkySettings(notificationsEnabled = true, notificationThresholdDegrees = 30.0)

    private fun voo(icao: String, elevacao: Double) =
        overheadFlight(aircraft = aircraft(icao24 = icao), elevationDegrees = elevacao)

    @Test
    fun `desligadas nao avisam e nem sequer consultam a persistencia`() = runTest {
        // A ordem importa: o interruptor é verificado antes de tudo. Sem isso, toda a gente — porque
        // isto está desligado de origem — pagaria uma consulta a Room por cada ciclo de fundo.
        val decisao = useCase(SkySettings(notificationsEnabled = false))(listOf(voo("aaa", 80.0)))

        assertEquals(NotificationDecision.Skip, decisao)
        coVerify(exactly = 0) { sightingRepository.wasNotifiedRecently(any(), any()) }
    }

    @Test
    fun `sem permissao do sistema nao avisa`() = runTest {
        // Lida a cada uso: o utilizador pode ter desligado as notificações da app nas definições do
        // Android sem a app saber.
        permissionGranted = false

        assertEquals(NotificationDecision.Skip, useCase(ligadas)(listOf(voo("aaa", 80.0))))
        permissionGranted = true
    }

    @Test
    fun `avisa a aeronave mais alta acima do limiar`() = runTest {
        coEvery { sightingRepository.wasNotifiedRecently(any(), any()) } returns false

        val decisao = useCase(ligadas)(listOf(voo("aaa", 35.0), voo("bbb", 77.0)))

        assertTrue(decisao is NotificationDecision.Notify)
        assertEquals("bbb", (decisao as NotificationDecision.Notify).flight.aircraft.icao24)
    }

    @Test
    fun `cinco aeronaves acima do limiar dao um aviso, nao cinco`() = runTest {
        // Cinco notificações ao mesmo tempo seriam motivo para desligar a feature no mesmo minuto.
        coEvery { sightingRepository.wasNotifiedRecently(any(), any()) } returns false
        val muitas = (1..5).map { voo("a$it", 40.0 + it) }

        val decisao = useCase(ligadas)(muitas)

        assertTrue(decisao is NotificationDecision.Notify)
        assertEquals("a5", (decisao as NotificationDecision.Notify).flight.aircraft.icao24)
    }

    @Test
    fun `nada acima do limiar nao avisa`() = runTest {
        assertEquals(NotificationDecision.Skip, useCase(ligadas)(listOf(voo("aaa", 12.0))))
    }

    @Test
    fun `uma aeronave ja avisada nao volta a avisar`() = runTest {
        coEvery { sightingRepository.wasNotifiedRecently("bbb", any()) } returns true

        assertEquals(NotificationDecision.Skip, useCase(ligadas)(listOf(voo("bbb", 70.0))))
    }

    @Test
    fun `um ciclo sem voos nenhuns nao avisa`() = runTest {
        // FR-009 pelo outro lado: um ciclo falhado nunca chega aqui, e um ciclo vazio não inventa.
        assertEquals(NotificationDecision.Skip, useCase(ligadas)(emptyList()))
    }

    @Test
    fun `uma falha a consultar a persistencia nao avisa, em vez de avisar as cegas`() = runTest {
        // A assimetria é deliberada: não avisar custa um aviso entre os muitos que esta feature já
        // não consegue dar; avisar por engano interrompe alguém com base num estado que não se
        // conseguiu ler.
        coEvery { sightingRepository.wasNotifiedRecently(any(), any()) } throws IllegalStateException("boom")

        assertEquals(NotificationDecision.Skip, useCase(ligadas)(listOf(voo("aaa", 70.0))))
    }

    @Test
    fun `o limiar escolhido pelo utilizador e respeitado`() = runTest {
        coEvery { sightingRepository.wasNotifiedRecently(any(), any()) } returns false
        val exigente = SkySettings(notificationsEnabled = true, notificationThresholdDegrees = 75.0)

        assertEquals(NotificationDecision.Skip, useCase(exigente)(listOf(voo("aaa", 70.0))))
        assertTrue(useCase(exigente)(listOf(voo("aaa", 80.0))) is NotificationDecision.Notify)
    }
}
