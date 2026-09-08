package com.mysky.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.repository.RouteTableRepository
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
 * **Não conhece o WorkManager.** Fala com o [RouteTableRepository], que já devolve estado de
 * domínio — a mesma fronteira que mantém o ecrã principal longe das exceções de rede (AD-017).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val routeTableRepository: RouteTableRepository,
) : ViewModel() {

    private val tableInfo = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(tableInfo, routeTableRepository.updateState) { base, update ->
            base.copy(updateState = update)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    init {
        refreshTableInfo()
    }

    /** Nunca transfere nada: lê o cabeçalho do ficheiro que já está no dispositivo (FR-019). */
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

    fun onUpdateRouteTable() {
        routeTableRepository.requestUpdate()
    }

    /** Depois de uma atualização bem sucedida, a data em uso mudou e tem de ser relida. */
    fun onUpdateFinished(state: RouteUpdateState) {
        if (state is RouteUpdateState.Success) refreshTableInfo()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
