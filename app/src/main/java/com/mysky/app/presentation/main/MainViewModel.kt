package com.mysky.app.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.presentation.sky.SkySession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel do ecrã principal.
 *
 * Depois da AD-011 é um consumidor fino: o laço de atualização vive na [SkySession], partilhada com
 * o ecrã de detalhe, e aqui fica apenas o que é exclusivo deste ecrã — o estado da permissão e o
 * fluxo de rationale, porque este é o único ecrã que pede permissões.
 *
 * A subscrição da sessão tem de passar por `stateIn(viewModelScope, WhileSubscribed(...))` e nunca
 * por um `viewModelScope.launch { collect { ... } }`: este ViewModel sobrevive na pilha de
 * retrocesso enquanto o detalhe está aberto, e um `launch` prenderia o laço à vida dele em vez de à
 * visibilidade do ecrã — a app continuaria a consultar a rede em segundo plano sem qualquer sinal.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val skySession: SkySession,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val permission = MutableStateFlow(PermissionState.Unknown)

    val uiState: StateFlow<MainUiState> =
        combine(permission, skySession.observation) { permissionState, observation ->
            MainUiState(permission = permissionState, observation = observation)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainUiState())

    /** Reavalia a permissão sempre que o ecrã volta a ficar visível (FR-006 da 001). */
    fun onScreenVisible() {
        val granted = locationRepository.hasLocationPermission()
        permission.update { current ->
            when {
                granted -> PermissionState.Granted
                // Nunca foi pedida: continua a ser o momento do rationale, não uma recusa.
                current == PermissionState.Unknown -> PermissionState.Unknown
                current == PermissionState.PermanentlyDenied -> PermissionState.PermanentlyDenied
                else -> PermissionState.Denied
            }
        }
        wakeSessionIfGranted()
    }

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        permission.value = when {
            granted -> PermissionState.Granted
            canAskAgain -> PermissionState.Denied
            else -> PermissionState.PermanentlyDenied
        }
        wakeSessionIfGranted()
    }

    fun onManualRefresh() {
        skySession.requestRefresh()
    }

    /**
     * Avisa a sessão de que a permissão pode ter mudado, sem tentar adivinhar se isso importa.
     *
     * Quem sabe se importa é a sessão, que se lembra de ter saltado um ciclo por falta de
     * permissão. Decidir aqui, a partir do estado **anterior** deste ViewModel, era o que estava
     * errado: os dois efeitos que reagem à concessão — a reavaliação ao retomar e a resposta ao
     * diálogo — disparam na mesma transição e sem ordem garantida entre si. Bastava o primeiro pôr
     * a permissão em `Granted` para o segundo já ver `previous == Granted` e nenhum dos dois
     * acordar o laço: quem acabou de conceder ficava meio minuto à espera.
     */
    private fun wakeSessionIfGranted() {
        if (permission.value == PermissionState.Granted) skySession.onPermissionMayHaveChanged()
    }

    private companion object {
        /** Cobre uma rotação de ecrã sem cancelar e reiniciar trabalho já em curso. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
