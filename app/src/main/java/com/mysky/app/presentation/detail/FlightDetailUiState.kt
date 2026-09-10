package com.mysky.app.presentation.detail

import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.FlightPresence
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError
import com.mysky.app.presentation.sky.LoadPhase

/**
 * Estado do ecrã de detalhe. Um único data class imutável, com os estados compostos **derivados**
 * e não armazenados — a mesma regra do `MainUiState`, para os dois ecrãs não divergirem em
 * conceitos.
 *
 * [phase] vem da observação partilhada sem alteração: sem ela o ecrã não conseguiria distinguir "a
 * obter a tua localização" de "a procurar aviões", que a tabela de precedência exige.
 */
data class FlightDetailUiState(
    /** `null` quando a rota chegou sem aeronave — erro de navegação, nunca ecrã em branco. */
    val icao24: String? = null,
    val phase: LoadPhase = LoadPhase.Idle,
    val presence: FlightPresence = FlightPresence.NeverObserved,
    val lastUpdatedEpochSeconds: Long? = null,
    val lastError: SkyError? = null,
    /** As unidades escolhidas, pelo mesmo caminho do ecrã principal (AD-020). */
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
) {
    /** O voo a apresentar: o atual, ou o último conhecido se a aeronave já saiu do céu. */
    val flight: OverheadFlight?
        get() = when (val current = presence) {
            is FlightPresence.Current -> current.flight
            is FlightPresence.LeftSky -> current.lastFlight
            FlightPresence.NeverObserved -> null
        }

    /** Os valores estão lá, mas já não são de agora (FR-020). */
    val hasLeftSky: Boolean get() = presence is FlightPresence.LeftSky

    /** Instante em que a aeronave foi vista pela última vez, quando já saiu do céu. */
    val lastSeenEpochSeconds: Long?
        get() = (presence as? FlightPresence.LeftSky)?.lastSeenEpochSeconds

    /** Ainda não houve observação nenhuma nesta sessão e há trabalho em curso. */
    val isWaitingFirstObservation: Boolean
        get() = presence is FlightPresence.NeverObserved && lastUpdatedEpochSeconds == null &&
            lastError == null

    /** Há dados no ecrã mas a última tentativa falhou (FR-022). */
    val hasStaleData: Boolean
        get() = lastError != null && presence !is FlightPresence.NeverObserved

    /** Erro sem nada por baixo: a única situação em que o erro ocupa o ecrã todo (FR-023). */
    val isBlockingError: Boolean
        get() = lastError != null && presence is FlightPresence.NeverObserved

    /**
     * Observou-se o céu com sucesso e esta aeronave não estava lá — sem nunca ter estado.
     *
     * Acontece ao entrar por uma rota antiga, e depois de o processo ser morto e restaurado com o
     * detalhe no topo da pilha.
     */
    val isAbsentFromSky: Boolean
        get() = presence is FlightPresence.NeverObserved && lastUpdatedEpochSeconds != null
}
