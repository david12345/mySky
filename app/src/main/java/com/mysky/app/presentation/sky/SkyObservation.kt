package com.mysky.app.presentation.sky

import com.mysky.app.domain.model.OverheadFlight
import com.mysky.app.domain.model.SkyError

/** Fase da operação em curso. As duas primeiras têm de ser distinguíveis na UI (FR-022 da 001). */
enum class LoadPhase { Idle, LocatingUser, LoadingFlights }

/**
 * O céu observado, tal como a [SkySession] o publica e os dois ecrãs o consomem.
 *
 * É o que era o `MainUiState` **menos** o que pertence só ao ecrã principal. Vive aqui, e não em
 * `presentation/main`, porque o detalhe não pode depender do pacote da lista — é precisamente o
 * acoplamento que a AD-011 existe para evitar.
 *
 * Uma falha **nunca** limpa [flights] nem [lastUpdatedEpochSeconds]: assinala dados possivelmente
 * desatualizados, não apaga o que o utilizador estava a ler.
 */
data class SkyObservation(
    val phase: LoadPhase = LoadPhase.Idle,
    /**
     * Conta as observações bem sucedidas desde o início da sessão.
     *
     * Existe porque [lastUpdatedEpochSeconds] tem granularidade de segundo e dois ciclos podem cair
     * no mesmo: quem precisa de saber se **esta** observação é nova — a redução de presença do
     * detalhe — ficaria a olhar para dois valores iguais e concluiria que nada mudou. O segundo é a
     * unidade certa para mostrar ao utilizador e a unidade errada para decidir identidade.
     */
    val observationSequence: Long = 0L,
    /** Já ordenada por elevação decrescente pelo caso de uso. */
    val flights: List<OverheadFlight> = emptyList(),
    /** `null` enquanto nunca houve uma consulta bem sucedida nesta sessão. */
    val lastUpdatedEpochSeconds: Long? = null,
    /** `null` quando a última tentativa correu bem. */
    val lastError: SkyError? = null,
)
