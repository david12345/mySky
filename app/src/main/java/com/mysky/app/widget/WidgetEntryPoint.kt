package com.mysky.app.widget

import android.content.Context
import com.mysky.app.domain.repository.SkyWidgetRepository
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.worker.SkyBackgroundWorkCoordinator
import com.mysky.app.worker.SkyWorkScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Como o widget chega às suas dependências.
 *
 * **O Hilt não injeta em `GlanceAppWidget` nem em `ActionCallback`.** O `@AndroidEntryPoint` do
 * receiver injeta o *receiver*, não o `SkyWidget()` que ele instancia com `new` — e a `ActionCallback`
 * é construída pelo próprio Glance a partir do nome da classe. Ambos são criados fora do grafo, por
 * isso a única via é pedir o grafo ao `Context`, que é o que isto faz.
 *
 * Sem isto, nada em `widget/` teria acesso ao repositório nem ao agendador.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun skyWidgetRepository(): SkyWidgetRepository
    fun timeProvider(): TimeProvider
    fun skyWorkScheduler(): SkyWorkScheduler
    fun skyBackgroundWorkCoordinator(): SkyBackgroundWorkCoordinator
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
