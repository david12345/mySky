package com.mysky.app.worker

import com.mysky.app.domain.model.SkySettings
import com.mysky.app.presentation.sky.FakeSettingsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A decisão de existir ou não existir trabalho de fundo.
 *
 * O teste que entrega a garantia à feature seguinte é o penúltimo: **com as notificações ligadas e
 * sem widget nenhum, o trabalho tem de existir**. `notificationsEnabled` é sempre falso nesta
 * feature, por isso essa linha da condição nunca é exercitada em uso — e sem um teste seria uma
 * promessa por escrito em vez de uma garantia. Com ele, a feature das notificações herda o
 * comportamento já verificado em vez de ter de reescrever o ciclo de vida.
 */
class SkyBackgroundWorkCoordinatorTest {

    private val scheduler = mockk<SkyWorkScheduler>(relaxed = true)

    private fun coordinator(
        hasWidget: Boolean,
        settings: SkySettings = SkySettings(),
    ) = SkyBackgroundWorkCoordinator(
        widgetPresence = { hasWidget },
        settingsRepository = FakeSettingsRepository(settings),
        scheduler = scheduler,
    )

    @Test
    fun `com um widget no ecra o trabalho passa a existir`() = runTest {
        coordinator(hasWidget = true).reconcile()

        verify(exactly = 1) { scheduler.schedulePeriodicRefresh(any()) }
        verify(exactly = 0) { scheduler.cancelPeriodicRefresh() }
    }

    @Test
    fun `sem widget nenhum o trabalho e cancelado`() = runTest {
        // Quem não tem widget não paga bateria nem consultas por ele (FR-020). É a diferença entre
        // uma app que se deixa instalada e uma que se desinstala.
        coordinator(hasWidget = false).reconcile()

        verify(exactly = 1) { scheduler.cancelPeriodicRefresh() }
        verify(exactly = 0) { scheduler.schedulePeriodicRefresh(any()) }
    }

    @Test
    fun `reconciliar varias vezes tem o mesmo efeito que uma`() = runTest {
        // A idempotência é o que permite chamar isto de todos os sítios plausíveis — arranque,
        // onEnabled, onUpdate, onDeleted — em vez de raciocinar sobre qual é o suficiente. E é
        // raciocinar sobre isso que produz o caso esquecido da FR-022.
        val coordinator = coordinator(hasWidget = true)

        coordinator.reconcile()
        coordinator.reconcile()
        coordinator.reconcile()

        verify(exactly = 3) { scheduler.schedulePeriodicRefresh(any()) }
        verify(exactly = 0) { scheduler.cancelPeriodicRefresh() }
    }

    @Test
    fun `a cadencia escolhida chega ao agendador`() = runTest {
        val settings = SkySettings(refreshIntervalMinutes = 60L)

        coordinator(hasWidget = true, settings = settings).reconcile()

        verify { scheduler.schedulePeriodicRefresh(settings) }
    }

    @Test
    fun `com notificacoes ligadas o trabalho existe mesmo sem widget`() = runTest {
        // A garantia que a feature seguinte herda. Esta linha da condição está escrita desde já,
        // apesar de `notificationsEnabled` ser sempre falso nesta feature — não é código morto por
        // descuido, é a diferença entre acrescentar uma chamada e reescrever a decisão.
        coordinator(hasWidget = false, settings = SkySettings(notificationsEnabled = true)).reconcile()

        verify(exactly = 1) { scheduler.schedulePeriodicRefresh(any()) }
        verify(exactly = 0) { scheduler.cancelPeriodicRefresh() }
    }

    @Test
    fun `uma falha a reconciliar nao sobe para quem chamou`() = runTest {
        // Reconciliar é sempre efeito secundário de outra coisa: um arranque, um widget adicionado,
        // uma definição gravada. Nenhuma dessas pode falhar porque o agendamento falhou.
        every { scheduler.schedulePeriodicRefresh(any()) } throws IllegalStateException("boom")

        coordinator(hasWidget = true).reconcile()
    }
}
