package com.mysky.app.data.repository

import com.mysky.app.data.local.SightingDao
import com.mysky.app.data.local.entity.SightingEntity
import com.mysky.app.domain.model.NotificationPolicy
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.repository.SightingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * O registo do que já foi avisado.
 *
 * **Só se gravam aeronaves avisadas.** A tabela existe para responder a uma pergunta — "já avisei esta
 * nos últimos 30 minutos?" — e não para guardar histórico. Gravar todas as observadas seria
 * implementar a feature do histórico por acidente, e acrescentar milhares de linhas por dia para
 * responder a uma pergunta que precisa de dezenas.
 *
 * Room, aqui, ao contrário das AD-007 e AD-013 que o rejeitaram: ali eram consultas por chave exata
 * sobre dados estáticos, e aqui há uma janela temporal e retenção — que é o caso para que Room existe.
 */
@Singleton
class SightingRepositoryImpl @Inject constructor(
    private val sightingDao: SightingDao,
) : SightingRepository {

    override fun recentSightings(limit: Int): Flow<List<OverheadFlight>> {
        TODO("Implementar durante a feature 'histórico de avistamentos' (ver .specify/)")
    }

    /**
     * Grava e limpa o que já não serve, na mesma chamada.
     *
     * A retenção em linha, e não num worker próprio: o volume é mínimo — no máximo um registo por
     * passagem realmente avisada — e um ponto de agendamento dedicado para isto contrariaria a própria
     * razão de ser da AD-016, que é não criar agendadores para trabalho que não os justifica.
     *
     * Nunca lança: falhar a gravar um aviso não pode derrubar o worker que acabou de o publicar. A
     * consequência de perder o registo é um segundo aviso possível para a mesma passagem, que é
     * incómodo; a de rebentar o worker é o widget e as notificações pararem.
     */
    override suspend fun record(flight: OverheadFlight, observedAtEpochSeconds: Long, notified: Boolean) {
        try {
            sightingDao.insert(flight.toEntity(observedAtEpochSeconds, notified))
            sightingDao.deleteOlderThan(observedAtEpochSeconds - NotificationPolicy.RETENTION_WINDOW_SECONDS)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // Ver a KDoc: perder o registo é menos mau do que perder o worker.
        }
    }

    /**
     * Na dúvida devolve `true` — ou seja, "já foi avisada".
     *
     * A assimetria é deliberada. Se a consulta falhar, **não avisar** é o erro barato: o utilizador
     * perde um aviso entre os muitos que esta feature já não consegue dar. Avisar por engano é o erro
     * caro: a mesma passagem a interromper alguém duas vezes é o género de coisa que faz desligar a
     * funcionalidade de vez.
     */
    override suspend fun wasNotifiedRecently(icao24: String, withinSeconds: Long): Boolean = try {
        val since = System.currentTimeMillis() / 1000 - withinSeconds
        sightingDao.countNotifiedSince(icao24, since) > 0
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        true
    }

    private fun OverheadFlight.toEntity(observedAtEpochSeconds: Long, notified: Boolean) = SightingEntity(
        icao24 = aircraft.icao24,
        callsign = aircraft.callsign,
        observedAtEpochSeconds = observedAtEpochSeconds,
        latitude = aircraft.position?.latitudeDegrees ?: 0.0,
        longitude = aircraft.position?.longitudeDegrees ?: 0.0,
        altitudeMeters = aircraft.geometricAltitudeMeters ?: aircraft.barometricAltitudeMeters ?: 0.0,
        horizontalDistanceMeters = horizontalDistanceMeters,
        bearingDegrees = bearingDegrees,
        elevationDegrees = elevationDegrees,
        notified = notified,
    )
}
