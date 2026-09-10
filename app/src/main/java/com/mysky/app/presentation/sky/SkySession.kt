package com.mysky.app.presentation.sky

import com.mysky.app.di.DefaultDispatcher
import com.mysky.app.domain.model.SkyError
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * O laço de atualização do céu, partilhado por todos os ecrãs que o observam (AD-011).
 *
 * Vivia dentro do `MainViewModel` (AD-008) e saiu de lá quando passou a haver um segundo ecrã a
 * olhar para a mesma observação. A forma mantém-se — um único laço sequencial, criado quando
 * alguém subscreve [observation] e cancelado 5 segundos depois do último subscritor — mas a
 * contagem de subscritores passa a somar os dois ecrãs. Daí saem, sem mutex e sem cache:
 *
 * - **um só pedido** por ciclo por mais ecrãs que estejam vivos (FR-017);
 * - **os mesmos valores nos dois ecrãs** no mesmo instante, porque a observação é uma só (FR-013);
 * - na transição lista → detalhe a contagem passa por 1 → 2 → 1 sem chegar a zero, por isso o laço
 *   não reinicia nem repete um pedido durante a sobreposição.
 *
 * Não sabe o que é uma permissão nem o que é um ecrã de definições: limita-se a perguntar aos
 * repositórios, a cada ciclo, se pode obter a posição e com que critérios deve procurar. É isso que
 * fecha a promessa que a AD-009 deixou aberta na primeira feature — os critérios deixam de estar
 * fixos no código. Quem trata do pedido e do rationale é o ecrã principal, e é ele que chama
 * [onPermissionMayHaveChanged] quando ela pode ter mudado — sem isso, quem acabou de conceder
 * esperaria pelo tique seguinte para ver a primeira lista.
 *
 * **Não pode ser injetada em `worker/` nem em `widget/`** (AD-003): esses passam pelo
 * `SkyRefreshWorker`. Uma sessão dentro de um worker faria trabalho de primeiro plano sobreviver ao
 * ecrã, que é exatamente o que esta forma existe para impedir.
 */
@ActivityRetainedScoped
class SkySession @Inject constructor(
    private val observeSky: ObserveSkyUseCase,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    lifecycle: ActivityRetainedLifecycle,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableState = MutableStateFlow(SkyObservation())

    /**
     * Canal conflado: dois toques seguidos em "atualizar" valem por um, mas um toque **nunca** se
     * perde. Um `SharedFlow` sem replay descartaria o que fosse emitido entre duas iterações do
     * laço, e é precisamente aí que um toque cairia sem deixar rasto.
     */
    private val manualRefresh = Channel<Unit>(Channel.CONFLATED)

    /**
     * O último ciclo não fez nada por não haver permissão.
     *
     * É o que permite a [onPermissionMayHaveChanged] acordar o laço **apenas** quando isso muda
     * alguma coisa. Escrito pelo laço e lido da thread principal, daí o `@Volatile`.
     */
    @Volatile
    private var skippedForPermission = false

    init {
        // O escopo é nosso, por isso a limpeza também tem de ser: o Hilt não cancela nada sozinho
        // quando o `ActivityRetainedComponent` termina, e sem isto ficava um laço pendurado.
        lifecycle.addOnClearedListener { scope.cancel() }
    }

    /**
     * O estado acumulado vive em [mutableState] e **sobrevive** ao laço ser cancelado e recriado —
     * é o que faz uma rotação de ecrã não repetir um pedido já concluído.
     */
    val observation: StateFlow<SkyObservation> = flow {
        coroutineScope {
            launch(start = CoroutineStart.UNDISPATCHED) { refreshLoop() }
            mutableState.collect { emit(it) }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SkyObservation())

    /** Renova já, e reinicia o relógio do ciclo. Serve os dois ecrãs: renovar num renova o outro. */
    fun requestRefresh() {
        manualRefresh.trySend(Unit)
    }

    /**
     * O ecrã que trata das permissões avisa que a permissão pode ter mudado.
     *
     * Acorda o laço **só** se o último ciclo tiver sido saltado por falta de permissão. É essa
     * condição — conhecida aqui e em mais lado nenhum — que separa os dois casos que antes se
     * confundiam: quem acabou de conceder tem de ver a lista já, e quem abriu a app com a permissão
     * de ontem não pode gerar um segundo pedido em cima do primeiro ciclo, que já ia buscar os
     * mesmos dados. Chamar isto de mais é inofensivo; o canal é conflado.
     */
    fun onPermissionMayHaveChanged() {
        if (skippedForPermission) requestRefresh()
    }

    private suspend fun refreshLoop() {
        while (coroutineContext.isActive) {
            val error = refreshOnce()
            // Um 429 alonga a espera em vez de ser reintentado: insistir gastaria o orçamento
            // diário exatamente quando ele já se esgotou.
            val waitSeconds = maxOf(
                REFRESH_INTERVAL_SECONDS,
                (error as? SkyError.RateLimited)?.retryAfterSeconds ?: 0L,
            )
            // O que vier primeiro: o tique ou um pedido manual. Assim um refresh manual reinicia o
            // relógio, em vez de ser seguido de um automático logo a seguir.
            withTimeoutOrNull(waitSeconds.seconds) { manualRefresh.receive() }
        }
    }

    /** @return o erro desta iteração, ou `null` se correu bem ou se não havia nada a fazer. */
    private suspend fun refreshOnce(): SkyError? {
        if (!locationRepository.hasLocationPermission()) {
            // Sem permissão não há ciclo nenhum a correr, e a fase não pode ficar presa a
            // "a carregar" — o estado é partilhado e não pode mentir a quem o leia noutro ecrã.
            skippedForPermission = true
            mutableState.update { it.copy(phase = LoadPhase.Idle) }
            return null
        }
        skippedForPermission = false

        mutableState.update { it.copy(phase = LoadPhase.LocatingUser) }

        // Uma posição pontual por ciclo, em vez de localização contínua: cobre o utilizador em
        // movimento por uma fração do custo de bateria.
        val observer = locationRepository.getCurrentLocation()
        if (observer == null) {
            // O repositório devolve `null` em vez de falhar, por isso é aqui — e só aqui — que
            // nasce esta variante. Sem ela, "sem GPS" seria indistinguível de "sem rede".
            return SkyError.LocationUnavailable.also { error ->
                mutableState.update { it.copy(phase = LoadPhase.Idle, lastError = error) }
            }
        }

        mutableState.update { it.copy(phase = LoadPhase.LoadingFlights) }

        // Uma leitura pontual, no início do ciclo, e não uma subscrição viva (AD-018). O ciclo
        // trabalha com este snapshot do princípio ao fim: como os critérios são um `data class`
        // passado por valor ao caso de uso, uma lista com critérios misturados é estruturalmente
        // impossível — não há nada para alguém se lembrar de fazer.
        val criteria = settingsRepository.settings.first().toCriteria()

        return observeSky(observer, criteria).fold(
            onSuccess = { flights ->
                mutableState.update {
                    it.copy(
                        phase = LoadPhase.Idle,
                        observationSequence = it.observationSequence + 1,
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
                // não apaga o que o utilizador já estava a ler.
                mutableState.update { it.copy(phase = LoadPhase.Idle, lastError = error) }
                error
            },
        )
    }

    private companion object {
        /** Fixo nesta feature; passa a configurável na feature de definições. */
        const val REFRESH_INTERVAL_SECONDS = 30L

        /** Cobre uma rotação de ecrã, e a passagem da lista para o detalhe, sem parar o laço. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
