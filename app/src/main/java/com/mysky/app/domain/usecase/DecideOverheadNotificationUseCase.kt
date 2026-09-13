package com.mysky.app.domain.usecase

import com.mysky.app.domain.model.NotificationPolicy
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.OverheadNotificationSelector
import com.mysky.app.domain.repository.NotificationPermission
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.repository.SightingRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Avisar, ou não. Nunca "avisar estes cinco". */
sealed interface NotificationDecision {
    data class Notify(val flight: OverheadFlight) : NotificationDecision
    data object Skip : NotificationDecision
}

/**
 * Junta a regra pura à deduplicação, que precisa de persistência.
 *
 * **A ordem das verificações não é arbitrária.** O interruptor vem primeiro de tudo: sem isso, cada
 * ciclo de cada utilizador que tem a feature desligada — que é toda a gente, por omissão — faria uma
 * consulta à base de dados para chegar à conclusão de que não devia fazer nada.
 */
class DecideOverheadNotificationUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sightingRepository: SightingRepository,
    private val notificationPermission: NotificationPermission,
) {

    suspend operator fun invoke(flights: List<OverheadFlight>): NotificationDecision {
        return try {
            val settings = settingsRepository.settings.first()
            if (!settings.notificationsEnabled) return NotificationDecision.Skip

            // Lida do sistema a cada vez, nunca de cache: a permissão pode ter sido revogada nas
            // definições do Android entre dois ciclos, sem a app ser informada.
            if (!notificationPermission.isGranted()) return NotificationDecision.Skip

            val candidate = OverheadNotificationSelector.selectCandidate(
                flights = flights,
                thresholdDegrees = settings.notificationThresholdDegrees,
            ) ?: return NotificationDecision.Skip

            val alreadyNotified = sightingRepository.wasNotifiedRecently(
                icao24 = candidate.aircraft.icao24,
                withinSeconds = NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS,
            )
            if (alreadyNotified) return NotificationDecision.Skip

            NotificationDecision.Notify(candidate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // **Não avisar** é sempre o erro barato. Avisar por engano interrompe alguém com base num
            // estado que não se conseguiu ler, e é o género de coisa que faz desligar a feature.
            NotificationDecision.Skip
        }
    }
}
