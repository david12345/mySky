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

/**
 * O DataStore das preferências do utilizador.
 *
 * Qualificado, tal como [WidgetSnapshotStore], por uma razão concreta: sem qualificador, quem
 * injetasse um `DataStore<Preferences>` receberia um dos dois **em silêncio**, conforme a ordem dos
 * bindings — e o pior desfecho seria o trabalho de fundo passar a escrever snapshots por cima das
 * escolhas do utilizador, de 30 em 30 minutos. Com os dois qualificados, o engano deixa de compilar.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

/** O DataStore do último resultado do trabalho de fundo. Ver [SettingsStore] para o porquê. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetSnapshotStore
