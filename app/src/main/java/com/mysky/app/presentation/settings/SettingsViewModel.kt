package com.mysky.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.RouteTableRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.presentation.sky.SkySession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel das definições.
 *
 * **Não conhece o WorkManager** (AD-017): a atualização da tabela de rotas chega-lhe já traduzida em
 * estado de domínio.
 *
 * As escritas de critério acordam o laço partilhado; as de unidade **não** (AD-019). A diferença não
 * é arbitrária: mudar de quilómetros para milhas não altera que aeronaves estão no céu, e pedir
 * dados por causa disso gastaria um crédito do orçamento para obter exatamente a mesma lista.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val routeTableRepository: RouteTableRepository,
    private val settingsRepository: SettingsRepository,
    private val skySession: SkySession,
) : ViewModel() {

    private val tableInfo = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        tableInfo,
        routeTableRepository.updateState,
        settingsRepository.settings,
    ) { base, update, settings ->
        base.copy(updateState = update, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    init {
        refreshTableInfo()
    }

    // --- Critérios: gravam e acordam o laço ------------------------------------------------------

    fun onRadiusChanged(meters: Double) = updateCriteria { it.copy(detectionRadiusMeters = meters) }

    fun onMinElevationChanged(degrees: Double) = updateCriteria { it.copy(minElevationDegrees = degrees) }

    fun onMinAltitudeChanged(meters: Double) = updateCriteria { it.copy(minAltitudeMeters = meters) }

    /** Repõe **só** o que é desta feature; a tabela de rotas fica onde está (AD-017). */
    fun onResetToDefaults() = updateCriteria { it.withDefaults() }

    // --- Unidades: gravam e não acordam nada ----------------------------------------------------

    fun onDistanceUnitChanged(unit: DistanceUnit) = updatePresentation { it.copy(distanceUnit = unit) }

    fun onAltitudeUnitChanged(unit: AltitudeUnit) = updatePresentation { it.copy(altitudeUnit = unit) }

    /**
     * Uma alteração de critério pede dados novos.
     *
     * A sessão lê os critérios no início de cada ciclo (AD-018), por isso a alteração apareceria
     * sozinha dentro de 30 segundos — o que já cumpriria o critério de sucesso. Acordar o laço é
     * cortesia sobre essa garantia, e o canal é conflado: um utilizador a arrastar um controlo não
     * gasta mais do que um pedido.
     */
    private fun updateCriteria(transform: (SkySettings) -> SkySettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            skySession.requestRefresh()
        }
    }

    private fun updatePresentation(transform: (SkySettings) -> SkySettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    // --- Tabela de rotas (003), inalterado -------------------------------------------------------

    fun onUpdateRouteTable() {
        routeTableRepository.requestUpdate()
    }

    fun onUpdateFinished(state: RouteUpdateState) {
        if (state is RouteUpdateState.Success) refreshTableInfo()
    }

    private fun refreshTableInfo() {
        viewModelScope.launch {
            val info = routeTableRepository.tableInfo()
            tableInfo.update {
                it.copy(
                    routeTableGeneratedAtEpochSeconds = info?.generatedAtEpochSeconds,
                    routeCount = info?.routeCount ?: 0,
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
