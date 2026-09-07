package com.mysky.app.presentation.sky

import com.mysky.app.NOW_EPOCH_SECONDS
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.domain.usecase.ObserveSkyUseCase
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.lifecycle.RetainedLifecycle
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Ciclo de vida do `ActivityRetainedComponent` falso.
 *
 * É uma interface pura do Hilt, sem nada de Android lá dentro, por isso a sessão continua testável
 * na JVM. [clear] simula o fim da Activity — é o que prova que o escopo interno é cancelado.
 */
class FakeRetainedLifecycle : ActivityRetainedLifecycle {

    private val listeners = mutableListOf<RetainedLifecycle.OnClearedListener>()

    override fun addOnClearedListener(listener: RetainedLifecycle.OnClearedListener) {
        listeners += listener
    }

    override fun removeOnClearedListener(listener: RetainedLifecycle.OnClearedListener) {
        listeners -= listener
    }

    fun clear() = listeners.toList().forEach { it.onCleared() }
}

/**
 * Sessão real sobre dependências falsas.
 *
 * Os testes do `MainViewModel` continuam a passar por aqui de propósito: o que a 001 fixou foi o
 * comportamento de ponta a ponta do ecrã, e trocar a sessão por um duplo transformaria esses testes
 * noutra coisa — deixariam de proteger a refatoração no momento em que mais é precisa.
 */
fun skySession(
    observeSky: ObserveSkyUseCase,
    locationRepository: LocationRepository,
    dispatcher: CoroutineDispatcher,
    timeProvider: TimeProvider = TimeProvider { NOW_EPOCH_SECONDS },
    lifecycle: ActivityRetainedLifecycle = FakeRetainedLifecycle(),
): SkySession = SkySession(
    observeSky = observeSky,
    locationRepository = locationRepository,
    timeProvider = timeProvider,
    dispatcher = dispatcher,
    lifecycle = lifecycle,
)
