package com.mysky.app.worker

import com.mysky.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * O único ponto que decide **se** deve existir trabalho de fundo (AD-026).
 *
 * Decide a partir do estado real — "há widgets neste momento?" — e não contando eventos. A diferença
 * parece de estilo e não é: o `onEnabled` de um widget só dispara na transição 0→1 *de sempre* para
 * aquele receiver. Quem já tinha o widget antes de a app ser atualizada nunca mais o recebe, e com uma
 * contagem de eventos ficaria sem trabalho de fundo **para sempre**, sem sintoma nenhum além de um
 * widget que nunca muda. Perguntar pela verdade dispensa migração: no primeiro arranque depois da
 * atualização, o widget está lá e é encontrado.
 *
 * `enqueueUniquePeriodicWork` é idempotente, por isso chamar isto de mais não custa nada — o que
 * permite chamá-lo de todos os sítios plausíveis em vez de raciocinar sobre qual é o suficiente.
 */
@Singleton
class SkyBackgroundWorkCoordinator @Inject constructor(
    private val widgetPresence: WidgetPresenceCheck,
    private val settingsRepository: SettingsRepository,
    private val scheduler: SkyWorkScheduler,
) {

    suspend fun reconcile() {
        try {
            val settings = settingsRepository.settings.first()
            // A condição já inclui as notificações, embora `notificationsEnabled` seja sempre falso
            // nesta feature. Não é código morto por descuido: é a diferença entre a feature seguinte
            // acrescentar uma chamada e a feature seguinte reescrever a decisão de agendamento.
            val shouldExist = widgetPresence.hasAnyWidget() || settings.notificationsEnabled

            if (shouldExist) scheduler.schedulePeriodicRefresh(settings) else scheduler.cancelPeriodicRefresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // Reconciliar é sempre um efeito secundário de outra coisa — um arranque, um widget
            // adicionado, uma definição gravada. Nenhuma dessas deve falhar porque o agendamento
            // falhou, e a próxima chamada volta a tentar de qualquer maneira.
        }
    }
}
