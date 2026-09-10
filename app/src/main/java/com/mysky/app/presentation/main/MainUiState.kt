package com.mysky.app.presentation.main

import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError
import com.mysky.app.presentation.sky.LoadPhase
import com.mysky.app.presentation.sky.SkyObservation

/** Estado da permissão de localização tal como o ecrã precisa de o distinguir. */
enum class PermissionState {
    /** Ainda não foi pedida: é o momento de mostrar o rationale (FR-001). */
    Unknown,
    Granted,
    Denied,

    /** Recusada ao ponto de o sistema já não mostrar o diálogo: só as definições resolvem. */
    PermanentlyDenied,
}

/**
 * Estado do ecrã principal. Um único data class imutável em vez de vários `StateFlow`, para que a
 * UI nunca observe combinações impossíveis.
 *
 * Depois da AD-011 compõe-se de duas partes: a [permission], exclusiva deste ecrã, e a
 * [observation], partilhada com o detalhe. Os estados compostos — céu vazio, dados desatualizados,
 * primeira carga — continuam **derivados**, não armazenados: guardá-los como campos permitiria
 * escrever "céu vazio" e "erro" ao mesmo tempo, que é precisamente a combinação que a tabela de
 * precedência do ecrã existe para evitar.
 */
data class MainUiState(
    val permission: PermissionState = PermissionState.Unknown,
    val observation: SkyObservation = SkyObservation(),
    /**
     * As unidades escolhidas pelo utilizador, que viajam **no estado** e não por um canal implícito
     * (AD-020). Chegam aos composables como mais um campo do que eles já leem.
     */
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
) {
    val phase: LoadPhase get() = observation.phase

    /** Já ordenada por elevação decrescente pelo caso de uso. */
    val flights: List<OverheadFlight> get() = observation.flights

    /** `null` enquanto nunca houve uma consulta bem sucedida. */
    val lastUpdatedEpochSeconds: Long? get() = observation.lastUpdatedEpochSeconds

    /** `null` quando a última tentativa correu bem. */
    val lastError: SkyError? get() = observation.lastError

    /** Consulta com sucesso e nenhuma aeronave a cumprir os critérios (FR-023). */
    val isSkyEmpty: Boolean
        get() = lastError == null && lastUpdatedEpochSeconds != null && flights.isEmpty()

    /** Há resultados no ecrã mas a última tentativa falhou (FR-025). */
    val hasStaleResults: Boolean
        get() = lastError != null && flights.isNotEmpty()

    /** Ainda não há nada para mostrar e há trabalho em curso. */
    val isFirstLoad: Boolean
        get() = lastUpdatedEpochSeconds == null && phase != LoadPhase.Idle

    /** Erro sem nada no ecrã por baixo: é a única situação em que o erro ocupa o ecrã todo. */
    val isBlockingError: Boolean
        get() = lastUpdatedEpochSeconds == null && lastError != null
}
