package com.mysky.app.domain.usecase

import com.mysky.app.domain.model.GeoPosition
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.repository.FlightRepository
import com.mysky.app.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Compõe localização + fonte de voos + [DetectOverheadFlightsUseCase] numa única operação,
 * partilhada pelo ecrã principal, pelo widget e pelo worker de notificações.
 *
 * TODO(feature/sky-list): implementar — obter as definições atuais, pedir ao [FlightRepository]
 *  as aeronaves nas caixas envolventes calculadas para o raio configurado, deduplicar por `icao24`
 *  (necessário quando o círculo é dividido no antimeridiano) e delegar a deteção.
 *  Especificação a definir via /speckit-specify antes de implementar.
 */
class ObserveSkyUseCase @Inject constructor(
    private val flightRepository: FlightRepository,
    private val settingsRepository: SettingsRepository,
    private val detectOverheadFlights: DetectOverheadFlightsUseCase,
) {
    suspend operator fun invoke(observer: GeoPosition): Result<List<OverheadFlight>> {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }
}
