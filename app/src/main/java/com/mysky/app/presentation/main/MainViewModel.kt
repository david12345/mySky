package com.mysky.app.presentation.main

import androidx.lifecycle.ViewModel
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel do ecrã principal.
 *
 * TODO(feature/sky-list): implementar
 *  - `refresh()`: obter localização, chamar [ObserveSkyUseCase], atualizar [MainUiState];
 *  - refresh automático segundo `settings.refreshIntervalMinutes`, apenas com o ecrã em primeiro
 *    plano (o trabalho em background é do worker, não daqui);
 *  - reação à concessão/negação da permissão de localização.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeSky: ObserveSkyUseCase,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refresh() {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }

    fun onLocationPermissionResult(granted: Boolean) {
        TODO("Implementar durante a feature 'lista de aviões' (ver .specify/)")
    }
}
