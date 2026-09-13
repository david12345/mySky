package com.mysky.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver declarado no manifesto.
 *
 * Cada callback aqui **tem de chamar `super`**: o `GlanceAppWidgetReceiver` já sobrepõe `onUpdate`,
 * `onDeleted` e `onAppWidgetOptionsChanged`, e é lá que ele desenha. Um override que se esqueça do
 * `super` não dá exceção nenhuma — dá um widget permanentemente em branco, que é o pior sintoma
 * possível porque parece uma falha de desenho e não de código.
 *
 * O que se acrescenta é sempre a mesma chamada: `reconcile()` (AD-026). Não se conta se o widget é o
 * primeiro ou o último — pergunta-se quantos existem, o que dispensa acertar em qual callback dispara
 * em que transição.
 */
@AndroidEntryPoint
class SkyWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SkyWidget()

    /**
     * Escopo próprio porque um `BroadcastReceiver` morre assim que o callback devolve.
     *
     * `reconcile()` é rápido e idempotente: se o processo for morto a meio, a chamada seguinte —
     * no arranque, ou no callback seguinte — reconcilia na mesma. É por isso que não é preciso
     * `goAsync()` nem garantia de conclusão aqui.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.reconcileBackgroundWork()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.reconcileBackgroundWork()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Também aqui, e não só no `onEnabled`: é este o callback que dispara depois de a app ser
        // atualizada, e é o que recupera o widget de quem já o tinha antes desta versão (FR-022).
        context.reconcileBackgroundWork()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        context.reconcileBackgroundWork()
    }

    private fun Context.reconcileBackgroundWork() {
        val coordinator = widgetEntryPoint().skyBackgroundWorkCoordinator()
        scope.launch { coordinator.reconcile() }
    }
}
