package com.mysky.app

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Substitui o `Dispatchers.Main` que o `viewModelScope` usa por um dispatcher de teste.
 *
 * O scheduler é o mesmo que o `runTest` usa, para que o tempo virtual seja um só: sem isto, um
 * `advanceTimeBy` no teste não moveria o relógio do laço de atualização.
 */
class MainDispatcherRule(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    private val dispatcher: TestDispatcher = StandardTestDispatcher(scheduler),
) : TestWatcher() {

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()

    /** Contexto a passar ao `runTest` para partilhar o mesmo tempo virtual. */
    val testContext get() = EmptyCoroutineContext + dispatcher
}
