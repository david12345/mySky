package com.mysky.app.presentation.main

import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError

/** Estado da permissão de localização tal como o ecrã precisa de o distinguir. */
enum class PermissionState {
    /** Ainda não foi pedida: é o momento de mostrar o rationale (FR-001). */
    Unknown,
    Granted,
    Denied,

    /** Recusada ao ponto de o sistema já não mostrar o diálogo: só as definições resolvem. */
    PermanentlyDenied,
}

/** Fase da operação em curso. As duas primeiras têm de ser distinguíveis na UI (FR-022). */
enum class LoadPhase { Idle, LocatingUser, LoadingFlights }

/**
 * Estado do ecrã principal. Um único data class imutável em vez de vários `StateFlow`, para que a
 * UI nunca observe combinações impossíveis.
 *
 * Os estados compostos — céu vazio, dados desatualizados, primeira carga — são **derivados**, não
 * armazenados. Guardá-los como campos permitiria escrever "céu vazio" e "erro" ao mesmo tempo, que
 * é precisamente a combinação que a tabela de precedência do ecrã existe para evitar.
 */
data class MainUiState(
    val permission: PermissionState = PermissionState.Unknown,
    val phase: LoadPhase = LoadPhase.Idle,
    /** Já ordenada por elevação decrescente pelo caso de uso. */
    val flights: List<OverheadFlight> = emptyList(),
    /** `null` enquanto nunca houve uma consulta bem sucedida. */
    val lastUpdatedEpochSeconds: Long? = null,
    /** `null` quando a última tentativa correu bem. */
    val lastError: SkyError? = null,
) {
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
