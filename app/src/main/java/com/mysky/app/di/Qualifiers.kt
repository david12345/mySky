package com.mysky.app.di

import javax.inject.Qualifier

/** Dispatcher para I/O (rede, disco). Injetado para os testes poderem substituí-lo. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher por omissão para trabalho de CPU (cálculo geométrico sobre listas grandes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Scope de aplicação, para trabalho que deve sobreviver ao ecrã que o iniciou. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
