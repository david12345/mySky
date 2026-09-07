package com.mysky.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysky.app.domain.model.FlightPresence
import com.mysky.app.domain.usecase.TrackFlightPresenceUseCase
import com.mysky.app.presentation.sky.SkyObservation
import com.mysky.app.presentation.sky.SkySession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel do ecrã de detalhe.
 *
 * Consumidor fino da [SkySession] partilhada (AD-011): não vai à rede, não tem laço e não pede
 * permissões. Olha para a mesma observação que a lista, filtra a aeronave da rota e acrescenta a
 * única coisa que é sua — a memória de presença, que é por ecrã e por aeronave.
 *
 * A subscrição **tem de** ser `stateIn(viewModelScope, WhileSubscribed(...))` e nunca um
 * `viewModelScope.launch { collect { ... } }`: um `launch` prenderia o laço partilhado à vida deste
 * ViewModel, que sobrevive na pilha de retrocesso, e a app continuaria a consultar a rede em
 * segundo plano sem qualquer sinal visível.
 */
@HiltViewModel
class FlightDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val skySession: SkySession,
    private val trackFlightPresence: TrackFlightPresenceUseCase,
) : ViewModel() {

    val icao24: String? = savedStateHandle[ARG_ICAO24]

    /**
     * A memória entre observações. Vive aqui e não na sessão partilhada, que tem de continuar sem
     * estado por aeronave para poder servir os dois ecrãs.
     */
    private var presence: FlightPresence = FlightPresence.NeverObserved

    /**
     * Número da última observação já reduzida. Impede que a mesma seja contada duas vezes.
     *
     * É o número de sequência e não a marca temporal: dois ciclos podem cair no mesmo segundo, e
     * nesse caso a marca temporal diria "nada de novo" a uma observação que era nova — e uma
     * aeronave que tivesse saído do céu nesse segundo ficaria a ser mostrada como estando lá.
     */
    private var lastReducedSequence: Long = 0L

    val uiState: StateFlow<FlightDetailUiState> = skySession.observation
        .map { observation ->
            presence = reduce(observation)
            FlightDetailUiState(
                icao24 = icao24,
                phase = observation.phase,
                presence = presence,
                lastUpdatedEpochSeconds = observation.lastUpdatedEpochSeconds,
                lastError = observation.lastError,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            FlightDetailUiState(icao24 = icao24),
        )

    /**
     * A sessão emite várias vezes por ciclo — cada mudança de fase é uma emissão — e a mesma
     * observação não pode ser reduzida duas vezes. Só o avanço da marca temporal conta como
     * observação nova; tudo o resto entra como `flights = null`, que o domínio já define como "não
     * sei nada de novo sobre o céu" e nunca como uma aeronave que saiu dele.
     */
    private fun reduce(observation: SkyObservation): FlightPresence {
        val isNewObservation = observation.observationSequence != lastReducedSequence
        val next = trackFlightPresence(
            previous = presence,
            icao24 = icao24,
            flights = if (isNewObservation) observation.flights else null,
            observedAtEpochSeconds = observation.lastUpdatedEpochSeconds ?: 0L,
        )
        if (isNewObservation) lastReducedSequence = observation.observationSequence
        return next
    }

    /** Renova já. É o mesmo canal da lista: renovar aqui renova os dois ecrãs. */
    fun onManualRefresh() {
        skySession.requestRefresh()
    }

    companion object {
        const val ARG_ICAO24 = "icao24"

        /** Cobre uma rotação de ecrã sem cancelar e reiniciar trabalho já em curso. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
