package com.mysky.app.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysky.app.domain.model.OverheadCriteria
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel do ecrã principal.
 *
 * A atualização periódica vive aqui e não no WorkManager (AD-008): um único laço sequencial, criado
 * quando alguém observa [uiState] e cancelado 5 segundos depois de o último observador desaparecer.
 * Duas propriedades caem de graça dessa forma:
 *
 * - **parar em segundo plano** (FR-019) é o ciclo de vida a fazer o seu trabalho, não código nosso;
 * - **não haver pedidos concorrentes** (FR-020) é consequência de haver uma só corrotina a fazer
 *   trabalho, sequencialmente — sem mutex nem flag de "em curso".
 *
 * O estado acumulado vive em [mutableState] e **sobrevive** ao laço ser cancelado e recriado, que
 * é o que faz uma rotação de ecrã não repetir um pedido já concluído (FR-026).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeSky: ObserveSkyUseCase,
    private val locationRepository: LocationRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow(MainUiState())
    private val permission = MutableStateFlow(PermissionState.Unknown)

    /**
     * Canal conflado: dois toques seguidos em "atualizar" valem por um, mas um toque **nunca** se
     * perde.
     *
     * AD-008 previa aqui um `MutableSharedFlow` com capacidade extra 1. Um `SharedFlow` sem replay
     * descarta o que é emitido enquanto não há coletor, e entre duas iterações do laço há um
     * instante em que não há: um toque que caísse aí desaparecia sem deixar rasto, e ao utilizador
     * pareceria que o gesto não fez nada. Um canal conflado guarda o último toque e faz a iteração
     * seguinte começar logo — sequencialmente, portanto sem violar FR-020.
     */
    private val manualRefresh = Channel<Unit>(Channel.CONFLATED)

    val uiState: StateFlow<MainUiState> = flow {
        coroutineScope {
            launch(start = CoroutineStart.UNDISPATCHED) { runSkyLoop() }
            mutableState.collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainUiState())

    /** Reavalia a permissão sempre que o ecrã volta a ficar visível (FR-006). */
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
    }

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        permission.value = when {
            granted -> PermissionState.Granted
            canAskAgain -> PermissionState.Denied
            else -> PermissionState.PermanentlyDenied
        }
    }

    fun onManualRefresh() {
        manualRefresh.trySend(Unit)
    }

    /**
     * `collectLatest` cancela o trabalho em curso quando a permissão muda: uma permissão revogada
     * a meio de um pedido não pode deixar o resultado desse pedido aparecer no ecrã depois.
     */
    private suspend fun runSkyLoop() {
        permission.collectLatest { permissionState ->
            if (permissionState != PermissionState.Granted) {
                // `collectLatest` já cancelou o pedido em curso; repor a fase é o que impede que
                // `phase` fique congelado em `LoadingFlights` depois de a permissão ser revogada
                // (invariante 1 do contrato de UI). Hoje a precedência do ecrã tapa isto, mas o
                // estado é partilhado e não pode mentir a quem o venha a ler noutro sítio.
                mutableState.update {
                    it.copy(permission = permissionState, phase = LoadPhase.Idle)
                }
                return@collectLatest
            }
            mutableState.update { it.copy(permission = permissionState) }
            refreshLoop()
        }
    }

    private suspend fun refreshLoop() {
        while (coroutineContext.isActive) {
            val error = refreshOnce()
            // Um 429 alonga a espera em vez de ser reintentado: insistir gastaria o orçamento
            // diário exatamente quando ele já se esgotou (FR-021).
            val waitSeconds = maxOf(
                REFRESH_INTERVAL_SECONDS,
                (error as? SkyError.RateLimited)?.retryAfterSeconds ?: 0L,
            )
            // O que vier primeiro: o tique ou um pedido manual. Assim um refresh manual reinicia
            // o relógio, em vez de ser seguido de um automático logo a seguir.
            withTimeoutOrNull(waitSeconds.seconds) { manualRefresh.receive() }
        }
    }

    /** @return o erro desta iteração, ou `null` se correu bem. */
    private suspend fun refreshOnce(): SkyError? {
        mutableState.update { it.copy(phase = LoadPhase.LocatingUser) }

        // Uma posição pontual por ciclo, em vez de localização contínua: cobre o utilizador em
        // movimento por uma fração do custo de bateria.
        val observer = locationRepository.getCurrentLocation()
        if (observer == null) {
            // O repositório devolve `null` em vez de falhar, por isso é aqui — e só aqui — que
            // nasce esta variante. Sem ela, "sem GPS" seria indistinguível de "sem rede" (FR-024).
            return SkyError.LocationUnavailable.also { error ->
                mutableState.update { it.copy(phase = LoadPhase.Idle, lastError = error) }
            }
        }

        mutableState.update { it.copy(phase = LoadPhase.LoadingFlights) }
        return observeSky(observer, OverheadCriteria()).fold(
            onSuccess = { flights ->
                mutableState.update {
                    it.copy(
                        phase = LoadPhase.Idle,
                        flights = flights,
                        lastUpdatedEpochSeconds = timeProvider.nowEpochSeconds(),
                        lastError = null,
                    )
                }
                null
            },
            onFailure = { throwable ->
                val error = throwable as? SkyError ?: SkyError.Unexpected(throwable)
                // A lista anterior fica: uma falha assinala dados possivelmente desatualizados,
                // não apaga o que o utilizador já estava a ler (FR-025).
                mutableState.update { it.copy(phase = LoadPhase.Idle, lastError = error) }
                error
            },
        )
    }

    private companion object {
        /** Fixo nesta feature; passa a configurável na feature de definições (FR-016). */
        const val REFRESH_INTERVAL_SECONDS = 30L

        /** Cobre uma rotação de ecrã sem cancelar e reiniciar trabalho já em curso (FR-019). */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
